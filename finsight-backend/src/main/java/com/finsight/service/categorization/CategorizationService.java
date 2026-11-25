package com.finsight.service.categorization;

import com.finsight.api.dto.request.CategorizationRuleRequest;
import com.finsight.api.dto.response.CategorizationRuleResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.CategorizationRule;
import com.finsight.domain.entity.Category;
import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.CategorySource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.repository.CategorizationRuleRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.category.CategoryService;
import com.finsight.service.privacy.AuditLogger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests categories for transaction descriptions. Personal rules (learned from the user's corrections)
 * take precedence over the built-in keyword rules. Suggestions are only ever suggestions: imported
 * transactions stay pending until the user reviews them, and any category can be changed.
 */
@Service
@RequiredArgsConstructor
public class CategorizationService {

    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final AuditLogger auditLogger;

    public record Suggestion(Category category, CategorySource source) {
    }

    /** A suggester preloaded with one user's rules and categories, for categorizing many rows at once. */
    public interface Suggester {
        Optional<Suggestion> suggest(String description, TransactionType type);
    }

    @Transactional(readOnly = true)
    public Suggester suggesterFor(UUID userId) {
        List<CategorizationRule> personal = ruleRepository.findAllByUserIdOrderByKeywordAsc(userId).stream()
            // Longer keywords are more specific, so they are tried first.
            .sorted(Comparator.comparingInt((CategorizationRule r) -> r.getKeyword().length()).reversed())
            .toList();
        personal.forEach(rule -> rule.getCategory().getName()); // initialize while the session is open

        Map<String, Category> systemByName = new HashMap<>();
        for (Category category : categoryRepository.findAllVisibleTo(userId)) {
            if (category.getUserId() == null) {
                systemByName.put(category.getName().toLowerCase(Locale.ROOT), category);
            }
        }

        return (description, type) -> {
            String normalized = TextNormalizer.normalize(description);
            for (CategorizationRule rule : personal) {
                if (TextNormalizer.containsWord(normalized, rule.getKeyword())
                    && rule.getCategory().getKind().accepts(type)) {
                    return Optional.of(new Suggestion(rule.getCategory(), CategorySource.PERSONAL_RULE));
                }
            }
            for (BuiltInRules.Rule rule : BuiltInRules.RULES) {
                if (!rule.types().contains(type)) {
                    continue;
                }
                Category category = systemByName.get(rule.categoryName().toLowerCase(Locale.ROOT));
                if (category == null) {
                    continue;
                }
                for (String keyword : rule.keywords()) {
                    if (TextNormalizer.containsWord(normalized, keyword)) {
                        return Optional.of(new Suggestion(category, CategorySource.RULE));
                    }
                }
            }
            return Optional.empty();
        };
    }

    @Transactional(readOnly = true)
    public List<CategorizationRuleResponse> listRules(UUID userId) {
        return ruleRepository.findAllByUserIdOrderByKeywordAsc(userId).stream().map(this::toResponse).toList();
    }

    /**
     * Creates the rule, or re-points an existing rule with the same keyword. Optionally re-categorizes the
     * user's pending transactions that match and were not categorized by the user.
     *
     * @return number of pending transactions that were re-categorized
     */
    @Transactional
    public int saveRule(UUID userId, CategorizationRuleRequest request) {
        String keyword = TextNormalizer.normalize(request.keyword());
        if (keyword.length() < 3) {
            throw new BusinessRuleException("Keyword must contain at least three letters or digits");
        }
        Category category = categoryService.findVisible(userId, request.categoryId());
        CategorizationRule rule = ruleRepository.findByUserIdAndKeyword(userId, keyword)
            .orElseGet(() -> CategorizationRule.builder().userId(userId).keyword(keyword).build());
        rule.setCategory(category);
        CategorizationRule saved = ruleRepository.save(rule);
        auditLogger.record(userId, "CATEGORY_RULE_SAVED", "CategorizationRule", saved.getId().toString(),
            "Personal categorization rule saved");

        if (!Boolean.TRUE.equals(request.applyToPending())) {
            return 0;
        }
        Specification<Transaction> pendingNotUserChosen = (root, query, cb) -> cb.and(
            cb.equal(root.get("userId"), userId),
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("reviewStatus"), TransactionReviewStatus.PENDING),
            cb.or(cb.isNull(root.get("categorySource")), cb.notEqual(root.get("categorySource"), CategorySource.USER)));
        int updated = 0;
        for (Transaction transaction : transactionRepository.findAll(pendingNotUserChosen)) {
            if (TextNormalizer.containsWord(TextNormalizer.normalize(transaction.getRedactedDescription()), keyword)
                && category.getKind().accepts(transaction.getType())) {
                transaction.setCategory(category);
                transaction.setCategorySource(CategorySource.PERSONAL_RULE);
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        CategorizationRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));
        ruleRepository.delete(rule);
        auditLogger.record(userId, "CATEGORY_RULE_DELETED", "CategorizationRule", ruleId.toString(),
            "Personal categorization rule deleted");
    }

    private CategorizationRuleResponse toResponse(CategorizationRule rule) {
        return new CategorizationRuleResponse(rule.getId(), rule.getKeyword(),
            categoryService.toResponse(rule.getCategory()), rule.getCreatedAt());
    }
}

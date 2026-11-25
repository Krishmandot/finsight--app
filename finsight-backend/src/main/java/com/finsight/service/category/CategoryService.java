package com.finsight.service.category;

import com.finsight.api.dto.request.CategoryRequest;
import com.finsight.api.dto.response.CategoryDetailResponse;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.Category;
import com.finsight.domain.enums.CategoryKind;
import com.finsight.domain.enums.TransactionType;
import com.finsight.repository.CategorizationRuleRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.privacy.AuditLogger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System categories are shared and read-only. Each user can add custom categories, rename them and delete
 * them; deleting a category in use requires moving its transactions to another category first, so no
 * transaction is ever left pointing at a missing category.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    public static final int MAX_CUSTOM_CATEGORIES = 100;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategorizationRuleRepository ruleRepository;
    private final AuditLogger auditLogger;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAllForUser(UUID userId) {
        return categoryRepository.findAllVisibleTo(userId).stream().map(this::toResponse).toList();
    }

    /** Categories usable for the given transaction type (e.g. no "Salary" for an expense). */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAllForUser(UUID userId, TransactionType type) {
        return categoryRepository.findAllVisibleTo(userId).stream()
            .filter(category -> type == null || category.getKind().accepts(type))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDetailResponse> findAllWithUsage(UUID userId) {
        Map<UUID, Long> usage = new HashMap<>();
        for (Object[] row : transactionRepository.countActiveByCategory(userId)) {
            usage.put((UUID) row[0], (Long) row[1]);
        }
        return categoryRepository.findAllVisibleTo(userId).stream()
            .map(category -> new CategoryDetailResponse(toResponse(category), usage.getOrDefault(category.getId(), 0L)))
            .toList();
    }

    /** A category the user may assign: a system category or one of their own. */
    @Transactional(readOnly = true)
    public Category findVisible(UUID userId, UUID categoryId) {
        return categoryRepository.findById(categoryId)
            .filter(category -> category.isVisibleTo(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    /** Resolves an optional category for a transaction of the given type, validating ownership and kind. */
    @Transactional(readOnly = true)
    public Category resolveForTransaction(UUID userId, UUID categoryId, TransactionType type) {
        if (categoryId == null) {
            return null;
        }
        Category category = findVisible(userId, categoryId);
        if (type != null && !category.getKind().accepts(type)) {
            throw new BusinessRuleException("The category \"" + category.getName() + "\" is for "
                + describe(category.getKind()) + " and cannot be used for this transaction type");
        }
        return category;
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = cleanName(request.name());
        if (categoryRepository.existsNameVisibleTo(userId, name, null)) {
            throw BusinessRuleException.conflict("A category named \"" + name + "\" already exists");
        }
        long ownCount = categoryRepository.findAllVisibleTo(userId).stream().filter(c -> c.isOwnedBy(userId)).count();
        if (ownCount >= MAX_CUSTOM_CATEGORIES) {
            throw new BusinessRuleException("You can create at most " + MAX_CUSTOM_CATEGORIES + " custom categories");
        }
        Category category = categoryRepository.save(Category.builder()
            .name(name)
            .kind(request.kind())
            .userId(userId)
            .systemDefined(false)
            .iconCode("tag")
            .build());
        auditLogger.record(userId, "CATEGORY_CREATED", "Category", category.getId().toString(), "Custom category created");
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
        Category category = findOwned(userId, categoryId);
        String name = cleanName(request.name());
        if (categoryRepository.existsNameVisibleTo(userId, name, categoryId)) {
            throw BusinessRuleException.conflict("A category named \"" + name + "\" already exists");
        }
        if (request.kind() != category.getKind() && request.kind() != CategoryKind.ANY
            && transactionRepository.countByCategoryIdAndDeletedAtIsNull(categoryId) > 0) {
            boolean incompatible = transactionRepository.countIncompatibleWithKind(userId, categoryId,
                request.kind() == CategoryKind.INCOME
                    ? List.of(TransactionType.DEBIT, TransactionType.REFUND, TransactionType.TRANSFER, TransactionType.UNKNOWN)
                    : List.of(TransactionType.CREDIT, TransactionType.TRANSFER, TransactionType.UNKNOWN)) > 0;
            if (incompatible) {
                throw BusinessRuleException.conflict(
                    "Some transactions in this category don't match the new kind. Move them first or choose \"Any\".");
            }
        }
        category.setName(name);
        category.setKind(request.kind());
        auditLogger.record(userId, "CATEGORY_UPDATED", "Category", categoryId.toString(), "Custom category updated");
        return toResponse(categoryRepository.save(category));
    }

    /**
     * Deletes a custom category. If active transactions use it, {@code reassignToId} is required and they are
     * moved there. Soft-deleted transactions are moved too (or uncategorized when no target is given) so the
     * foreign key never dangles.
     *
     * @return number of transactions that were moved
     */
    @Transactional
    public int delete(UUID userId, UUID categoryId, UUID reassignToId) {
        Category category = findOwned(userId, categoryId);
        long activeCount = transactionRepository.countByCategoryIdAndDeletedAtIsNull(categoryId);
        Category target = null;
        if (reassignToId != null) {
            if (reassignToId.equals(categoryId)) {
                throw new BusinessRuleException("Choose a different category to move transactions to");
            }
            target = findVisible(userId, reassignToId);
            if (target.getKind() != CategoryKind.ANY && target.getKind() != category.getKind()) {
                throw new BusinessRuleException("Transactions can only be moved to a category of the same kind or to one marked \"Any\"");
            }
        } else if (activeCount > 0) {
            throw BusinessRuleException.conflict(activeCount + (activeCount == 1 ? " transaction uses" : " transactions use")
                + " this category. Choose a category to move them to before deleting it.");
        }

        int moved = categoryRepository.reassignTransactions(userId, category, target);
        ruleRepository.deleteAllByCategoryId(categoryId);
        categoryRepository.delete(categoryRepository.getReferenceById(categoryId));
        auditLogger.record(userId, "CATEGORY_DELETED", "Category", categoryId.toString(),
            "Custom category deleted; transactions reassigned");
        return moved;
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getParentName(),
            category.getIconCode(), category.isSystemDefined(), category.getKind());
    }

    private Category findOwned(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .filter(c -> c.isVisibleTo(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        if (!category.isOwnedBy(userId)) {
            throw new BusinessRuleException(org.springframework.http.HttpStatus.FORBIDDEN,
                "Built-in categories cannot be changed");
        }
        return category;
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new BusinessRuleException("Category name must be between 1 and 60 characters");
        }
        return name;
    }

    private static String describe(CategoryKind kind) {
        return switch (kind) {
            case INCOME -> "income";
            case EXPENSE -> "expenses and refunds";
            case ANY -> "any transaction";
        };
    }
}

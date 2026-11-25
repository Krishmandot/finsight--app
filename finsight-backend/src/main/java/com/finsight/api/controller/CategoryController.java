package com.finsight.api.controller;

import com.finsight.api.dto.request.CategorizationRuleRequest;
import com.finsight.api.dto.request.CategoryRequest;
import com.finsight.api.dto.response.CategorizationRuleResponse;
import com.finsight.api.dto.response.CategoryDetailResponse;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.domain.entity.AppUser;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.categorization.CategorizationService;
import com.finsight.service.category.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategorizationService categorizationService;

    /** System categories plus the caller's own, optionally limited to those usable for a transaction type. */
    @GetMapping
    public List<CategoryResponse> findAll(@AuthenticationPrincipal AppUser user,
                                          @RequestParam(required = false) TransactionType type) {
        return categoryService.findAllForUser(user.getId(), type);
    }

    @GetMapping("/usage")
    public List<CategoryDetailResponse> usage(@AuthenticationPrincipal AppUser user) {
        return categoryService.findAllWithUsage(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal AppUser user, @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(user.getId(), request);
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse update(@AuthenticationPrincipal AppUser user, @PathVariable UUID categoryId,
                                   @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(user.getId(), categoryId, request);
    }

    /** Deleting a category in use requires {@code reassignTo}; its transactions are moved there. */
    @DeleteMapping("/{categoryId}")
    public Map<String, Integer> delete(@AuthenticationPrincipal AppUser user, @PathVariable UUID categoryId,
                                       @RequestParam(required = false) UUID reassignTo) {
        return Map.of("moved", categoryService.delete(user.getId(), categoryId, reassignTo));
    }

    @GetMapping("/rules")
    public List<CategorizationRuleResponse> rules(@AuthenticationPrincipal AppUser user) {
        return categorizationService.listRules(user.getId());
    }

    @PostMapping("/rules")
    public Map<String, Integer> saveRule(@AuthenticationPrincipal AppUser user,
                                         @Valid @RequestBody CategorizationRuleRequest request) {
        return Map.of("recategorized", categorizationService.saveRule(user.getId(), request));
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@AuthenticationPrincipal AppUser user, @PathVariable UUID ruleId) {
        categorizationService.deleteRule(user.getId(), ruleId);
    }
}

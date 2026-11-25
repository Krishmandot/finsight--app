package com.finsight.service.transaction;

import com.finsight.api.dto.request.CreateTransactionRequest;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.Category;
import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.category.CategoryService;
import com.finsight.service.privacy.AuditLogger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private com.finsight.repository.ImportBatchRepository importBatchRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void manualCreationSetsSourcePendingReviewAndExcludedByDefault() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).name("Food & Dining").systemDefined(true).build();
        CreateTransactionRequest request = new CreateTransactionRequest(
            new BigDecimal("12.50"), "USD", LocalDate.now(), "Lunch", TransactionType.DEBIT, categoryId
        );
        when(categoryService.resolveForTransaction(userId, categoryId, com.finsight.domain.enums.TransactionType.DEBIT)).thenReturn(category);
        when(categoryService.toResponse(category)).thenReturn(
            new CategoryResponse(categoryId, "Food & Dining", null, null, true));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TransactionResponse response = transactionService.create(userId, request);

        assertEquals(IngestionSource.MANUAL, response.source());
        assertEquals(TransactionReviewStatus.PENDING, response.reviewStatus());
        assertFalse(response.includedInReports());
        assertEquals("Food & Dining", response.category().name());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(userId, captor.getValue().getUserId());
        assertEquals(category, captor.getValue().getCategory());
    }

    @Test
    void lookupIsScopedToOwnerAndActiveTransactions() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionId, otherUserId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.findById(otherUserId, transactionId));
        verify(transactionRepository).findByIdAndUserIdAndDeletedAtIsNull(eq(transactionId), eq(otherUserId));
    }

    @Test
    void inclusionRequiresApprovedReviewStatus() {
        UUID ownerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
            .id(transactionId)
            .userId(ownerId)
            .reviewStatus(TransactionReviewStatus.PENDING)
            .includedInReports(false)
            .build();
        when(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionId, ownerId))
            .thenReturn(Optional.of(transaction));

        com.finsight.api.exception.BusinessRuleException exception = assertThrows(com.finsight.api.exception.BusinessRuleException.class,
            () -> transactionService.updateInclusion(ownerId, transactionId, true));

        assertEquals(409, exception.getStatus().value());
        assertFalse(transaction.isIncludedInReports());
    }

    @Test
    void approvingThenIncludingAllowsReportParticipation() {
        UUID ownerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
            .id(transactionId)
            .userId(ownerId)
            .reviewStatus(TransactionReviewStatus.PENDING)
            .includedInReports(false)
            .build();
        when(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionId, ownerId))
            .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.updateReviewStatus(ownerId, transactionId, TransactionReviewStatus.APPROVED);
        TransactionResponse response = transactionService.updateInclusion(ownerId, transactionId, true);

        assertTrue(response.includedInReports());
        assertEquals(TransactionReviewStatus.APPROVED, response.reviewStatus());
    }
}
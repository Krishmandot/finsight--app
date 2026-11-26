package com.finsight.service.intelligence;

import com.finsight.api.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores the user's single overall monthly budget (table {@code monthly_budgets}). */
@Service
public class BudgetService {

    private static final BigDecimal MAX_BUDGET = new BigDecimal("1000000000");

    private final JdbcTemplate jdbc;

    public BudgetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> find(UUID userId) {
        return jdbc.query("SELECT amount FROM monthly_budgets WHERE user_id = ?",
            (rs, rowNum) -> rs.getBigDecimal(1), userId).stream().findFirst();
    }

    @Transactional
    public void save(UUID userId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_BUDGET) >= 0) {
            throw new BusinessRuleException("A budget must be more than zero and less than 1,000,000,000");
        }
        jdbc.update("INSERT INTO monthly_budgets (user_id, amount, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)"
                + " ON CONFLICT (user_id) DO UPDATE SET amount = EXCLUDED.amount, updated_at = CURRENT_TIMESTAMP",
            userId, amount.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public void clear(UUID userId) {
        jdbc.update("DELETE FROM monthly_budgets WHERE user_id = ?", userId);
    }
}

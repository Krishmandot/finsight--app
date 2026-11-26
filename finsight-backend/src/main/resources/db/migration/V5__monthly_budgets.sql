-- One overall monthly spending budget per user, used by the Financial Health Score.
CREATE TABLE monthly_budgets (
    user_id UUID PRIMARY KEY REFERENCES app_users(id),
    amount DECIMAL(15,2) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_monthly_budgets_amount CHECK (amount > 0)
);

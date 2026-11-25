ALTER TABLE transactions
    ADD COLUMN category_id UUID REFERENCES categories(id),
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN included_in_reports BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE transactions
    ALTER COLUMN review_status SET DEFAULT 'PENDING',
    ALTER COLUMN included_in_reports SET DEFAULT FALSE;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_review_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT chk_transactions_inclusion_requires_approval
        CHECK (included_in_reports = FALSE OR review_status = 'APPROVED');

CREATE INDEX idx_transactions_user_active_date
    ON transactions(user_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_category_id ON transactions(category_id);
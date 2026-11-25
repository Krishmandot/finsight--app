-- V4: user preferences, user-defined categories, statement import batches,
-- personal categorization rules and duplicate tracking.
-- Additive only: no existing rows are deleted.

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN preferred_currency VARCHAR(3) NOT NULL DEFAULT 'INR';

-- Refresh tokens are now stored as SHA-256 digests (previously BCrypt). Old BCrypt hashes can never
-- match a lookup again, so mark them revoked; API clients simply sign in again.
UPDATE refresh_tokens SET revoked = TRUE WHERE revoked = FALSE;

-- ---------------------------------------------------------------------------
-- Categories: system categories (user_id IS NULL) plus per-user custom categories
-- ---------------------------------------------------------------------------
ALTER TABLE categories
    ADD COLUMN user_id UUID REFERENCES app_users(id),
    ADD COLUMN category_kind VARCHAR(10) NOT NULL DEFAULT 'EXPENSE',
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE categories SET category_kind = 'INCOME'
    WHERE user_id IS NULL AND name IN ('Salary', 'Other Income');
UPDATE categories SET category_kind = 'ANY'
    WHERE user_id IS NULL AND name IN ('Transfer', 'Other');

-- Names were globally unique; now they are unique among system categories and within each user's set.
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_key;
CREATE UNIQUE INDEX uq_categories_system_name ON categories (lower(name)) WHERE user_id IS NULL;
CREATE UNIQUE INDEX uq_categories_user_name ON categories (user_id, lower(name)) WHERE user_id IS NOT NULL;
CREATE INDEX idx_categories_user_id ON categories (user_id);

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_kind CHECK (category_kind IN ('EXPENSE', 'INCOME', 'ANY')),
    ADD CONSTRAINT chk_categories_owner CHECK ((user_id IS NULL) = is_system_defined);

-- ---------------------------------------------------------------------------
-- Statement imports
-- ---------------------------------------------------------------------------
CREATE TABLE import_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    file_name VARCHAR(255) NOT NULL,
    file_format VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_REVIEW',
    total_rows INTEGER NOT NULL DEFAULT 0,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    duplicate_rows INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    CONSTRAINT chk_import_batches_format CHECK (file_format IN ('CSV', 'PDF')),
    CONSTRAINT chk_import_batches_status CHECK (status IN ('IN_REVIEW', 'COMPLETED'))
);
CREATE INDEX idx_import_batches_user_created ON import_batches (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Transactions
-- ---------------------------------------------------------------------------
ALTER TABLE transactions
    ADD COLUMN notes VARCHAR(1000),
    ADD COLUMN import_batch_id UUID REFERENCES import_batches(id),
    ADD COLUMN duplicate_of_id UUID REFERENCES transactions(id),
    ADD COLUMN category_source VARCHAR(20);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_category_source
        CHECK (category_source IS NULL OR category_source IN ('USER', 'RULE', 'PERSONAL_RULE'));

-- Enforced for new and updated rows; NOT VALID leaves any legacy rows untouched.
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_amount_positive CHECK (amount IS NULL OR amount > 0) NOT VALID;

CREATE INDEX idx_transactions_user_review
    ON transactions (user_id, review_status) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_user_report_date
    ON transactions (user_id, transaction_date)
    WHERE deleted_at IS NULL AND included_in_reports = TRUE;
CREATE INDEX idx_transactions_import_batch ON transactions (import_batch_id);

-- ---------------------------------------------------------------------------
-- Personal categorization rules learned from user corrections
-- ---------------------------------------------------------------------------
CREATE TABLE categorization_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    keyword VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categorization_rules_user_keyword UNIQUE (user_id, keyword)
);
CREATE INDEX idx_categorization_rules_user ON categorization_rules (user_id);

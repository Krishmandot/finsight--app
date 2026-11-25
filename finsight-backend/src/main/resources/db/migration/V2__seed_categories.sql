CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    parent_name VARCHAR(100),
    icon_code VARCHAR(50),
    is_system_defined BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO categories (name, parent_name, icon_code) VALUES
('Food & Dining', NULL, 'utensils'),
('Groceries', 'Food & Dining', 'shopping-basket'),
('Restaurants', 'Food & Dining', 'restaurant'),
('Transportation', NULL, 'car'),
('Fuel', 'Transportation', 'fuel'),
('Public Transport', 'Transportation', 'bus'),
('Utilities', NULL, 'zap'),
('Electricity', 'Utilities', 'bolt'),
('Internet', 'Utilities', 'wifi'),
('Shopping', NULL, 'shopping-bag'),
('Entertainment', NULL, 'film'),
('Subscriptions', 'Entertainment', 'repeat'),
('Healthcare', NULL, 'heart'),
('Education', NULL, 'book'),
('Travel', NULL, 'plane'),
('Financial Services', NULL, 'credit-card'),
('ATM Withdrawal', 'Financial Services', 'landmark'),
('Transfer', NULL, 'send'),
('Salary', NULL, 'briefcase'),
('Other Income', NULL, 'plus-circle'),
('Other', NULL, 'help-circle');

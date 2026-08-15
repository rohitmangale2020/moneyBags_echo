-- Reference DDL for the Transactions service ledger.
-- Local development uses Hibernate ddl-auto=update; apply this through the
-- database migration process for managed Oracle environments.

CREATE TABLE ledger_account (
    ledger_account_id VARCHAR2(36) PRIMARY KEY,
    code VARCHAR2(60) NOT NULL UNIQUE,
    name VARCHAR2(160) NOT NULL,
    account_type VARCHAR2(20) NOT NULL,
    current_balance NUMBER(19,4) NOT NULL,
    active NUMBER(1) NOT NULL
);

CREATE TABLE ledger_entry (
    ledger_entry_id VARCHAR2(36) PRIMARY KEY,
    transaction_ref VARCHAR2(40) NOT NULL,
    line_number NUMBER(10) NOT NULL,
    ledger_account_id VARCHAR2(36) NOT NULL,
    customer_account_id VARCHAR2(36),
    entry_type VARCHAR2(10) NOT NULL,
    amount NUMBER(19,4) NOT NULL,
    currency_code VARCHAR2(3) NOT NULL,
    posting_date DATE NOT NULL,
    description VARCHAR2(500),
    status VARCHAR2(12) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_ledger_entry_transaction_line UNIQUE (transaction_ref, line_number),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_account (ledger_account_id)
);

CREATE INDEX idx_ledger_entry_account_date ON ledger_entry (ledger_account_id, posting_date);
CREATE INDEX idx_ledger_entry_reference ON ledger_entry (transaction_ref);

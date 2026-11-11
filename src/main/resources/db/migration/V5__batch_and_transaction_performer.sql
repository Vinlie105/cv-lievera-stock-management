-- Add performer tracking to stock transactions (UC-17: who made the movement)
ALTER TABLE stock_transactions
    ADD COLUMN performed_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_stock_transactions_performed_by ON stock_transactions(performed_by);

-- Batch tracking for expiry-managed products (UC-18, UC-19, UC-21)
CREATE TABLE batches (
    id            BIGSERIAL      PRIMARY KEY,
    product_id    BIGINT         NOT NULL REFERENCES products(id),
    lot_number    VARCHAR(100),
    quantity      INTEGER        NOT NULL CHECK (quantity >= 0),
    expiry_date   DATE,
    received_date DATE           NOT NULL,
    cost_price    NUMERIC(15, 2),
    notes         VARCHAR(255),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_batches_product_id   ON batches(product_id);
CREATE INDEX idx_batches_expiry_date  ON batches(expiry_date);

-- Link transactions to the batch they drew from (enables FEFO audit trail, UC-19)
ALTER TABLE stock_transactions
    ADD COLUMN batch_id BIGINT REFERENCES batches(id) ON DELETE SET NULL;

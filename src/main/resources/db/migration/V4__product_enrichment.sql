ALTER TABLE products
    ADD COLUMN unit           VARCHAR(20)     NOT NULL DEFAULT 'PCS',
    ADD COLUMN brand          VARCHAR(100),
    ADD COLUMN cost_price     NUMERIC(15, 2),
    ADD COLUMN is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    ADD COLUMN expiry_tracked BOOLEAN         NOT NULL DEFAULT FALSE,
    ADD COLUMN barcode        VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS products_barcode_uidx ON products (barcode) WHERE barcode IS NOT NULL;

-- Baseline schema for CV Lievera Stock Management System
-- Uses IF NOT EXISTS so this is safe to run against an existing database

CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS suppliers (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    email          VARCHAR(255) UNIQUE,
    phone          VARCHAR(255),
    address        VARCHAR(255),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255)   NOT NULL,
    sku               VARCHAR(255)   NOT NULL UNIQUE,
    description       VARCHAR(255),
    unit_price        DECIMAL(15, 2) NOT NULL,
    quantity_in_stock INTEGER        NOT NULL,
    min_stock_level   INTEGER        NOT NULL,
    category_id       BIGINT REFERENCES categories (id),
    supplier_id       BIGINT REFERENCES suppliers (id),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_transactions (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT      NOT NULL REFERENCES products (id),
    transaction_type VARCHAR(50) NOT NULL,
    quantity         INTEGER     NOT NULL,
    notes            VARCHAR(255),
    reference_no     VARCHAR(255),
    transaction_date TIMESTAMP   NOT NULL,
    created_at       TIMESTAMP
);

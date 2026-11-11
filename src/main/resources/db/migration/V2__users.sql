CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(150) UNIQUE,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STAFF',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

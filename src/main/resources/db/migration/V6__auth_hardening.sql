-- Account lockout, forced password change, TOTP 2FA, token versioning
ALTER TABLE users
    ADD COLUMN failed_attempts       INT          NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMP,
    ADD COLUMN must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at   TIMESTAMP,
    ADD COLUMN token_version         INT          NOT NULL DEFAULT 0,
    ADD COLUMN totp_secret_enc       VARCHAR(255),
    ADD COLUMN totp_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_last_step        BIGINT;

-- Any pre-existing account must pick a new password (the old seeded admin used a default)
UPDATE users SET must_change_password = TRUE;

-- Opaque refresh tokens: only SHA-256 hashes are stored. Rotated on every use.
CREATE TABLE refresh_tokens (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    family_id    VARCHAR(36)  NOT NULL,
    family_start TIMESTAMP    NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    ip_address   VARCHAR(45)
);
CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

-- One-time 2FA recovery codes (hashed)
CREATE TABLE recovery_codes (
    id        BIGSERIAL   PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    used      BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes(user_id);

-- Tamper-evident audit log: hash chain + append-only enforcement
ALTER TABLE audit_logs
    ADD COLUMN prev_hash VARCHAR(64),
    ADD COLUMN hash      VARCHAR(64);
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_user_id_fkey;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

CREATE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();
CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_immutable();

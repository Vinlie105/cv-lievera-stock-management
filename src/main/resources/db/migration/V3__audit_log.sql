CREATE TABLE audit_logs (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    username       VARCHAR(100),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      BIGINT,
    change_summary TEXT,
    ip_address     VARCHAR(45),
    created_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- Aşama 6: ticket attachments. File contents live on a local volume (itsm.attachments.dir) under a random
-- storage key; only metadata is stored here.
CREATE TABLE ticket_attachments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES tickets (id),
    uploader_id   VARCHAR(64)  NOT NULL,
    visibility    VARCHAR(16)  NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(127) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(64)  NOT NULL UNIQUE,
    sha256        CHAR(64)     NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ticket_attachments_visibility_chk CHECK (visibility IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ticket_attachments_size_chk CHECK (size_bytes > 0)
);

CREATE INDEX ticket_attachments_ticket_idx ON ticket_attachments (ticket_id, created_at);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_chk CHECK (type IN (
    'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED', 'ATTACHMENT_ADDED'));

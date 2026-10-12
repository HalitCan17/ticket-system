-- Aşama 10: ticket categories and the customer's satisfaction rating.
-- (Withdrawing a NEW ticket is a status rule only: NEW -> CLOSED by the requester, no schema change.)

ALTER TABLE tickets
    ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'OTHER'
        CONSTRAINT tickets_category_chk CHECK (category IN ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER')),
    ADD COLUMN rating SMALLINT
        CONSTRAINT tickets_rating_chk CHECK (rating BETWEEN 1 AND 5),
    ADD COLUMN rating_comment TEXT,
    ADD COLUMN rated_at TIMESTAMPTZ,
    ADD CONSTRAINT tickets_rating_complete_chk CHECK ((rating IS NULL) = (rated_at IS NULL));

CREATE INDEX idx_tickets_category ON tickets (category);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_chk CHECK (type IN (
    'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED', 'ATTACHMENT_ADDED',
    'TICKET_RATED'));

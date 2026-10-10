-- Aşama 5: in-app notifications.

-- Role as last seen in the user's token; used to find managers for SLA breach notifications.
ALTER TABLE app_users ADD COLUMN role VARCHAR(16);

-- An SLA breach is notified once per ticket.
ALTER TABLE tickets ADD COLUMN sla_breach_notified_at TIMESTAMPTZ;

CREATE TABLE notifications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id  VARCHAR(64) NOT NULL,
    type          VARCHAR(40) NOT NULL,
    ticket_id     BIGINT      NOT NULL REFERENCES tickets (id),
    -- who caused it (user id, or 'system'); NULL for SLA breaches
    actor_id      VARCHAR(64),
    -- the ticket's status right after a status change
    ticket_status VARCHAR(32),
    created_at    TIMESTAMPTZ NOT NULL,
    read_at       TIMESTAMPTZ,
    CONSTRAINT notifications_type_chk CHECK (type IN (
        'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED'))
);

CREATE INDEX notifications_recipient_idx ON notifications (recipient_id, created_at DESC, id DESC);
CREATE INDEX notifications_unread_idx ON notifications (recipient_id) WHERE read_at IS NULL;

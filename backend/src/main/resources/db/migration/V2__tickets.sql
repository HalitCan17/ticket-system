-- Aşama 1: ticket core.
-- Users live in the identity provider (Keycloak later), so user references are plain
-- identifiers (future JWT subject), not foreign keys.

CREATE TABLE tickets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    priority        VARCHAR(16)  NOT NULL,
    requester_id    VARCHAR(64)  NOT NULL,
    assignee_id     VARCHAR(64),
    resolution_note TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT tickets_status_chk
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED')),
    CONSTRAINT tickets_priority_chk
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX tickets_requester_idx ON tickets (requester_id);
CREATE INDEX tickets_status_idx ON tickets (status);
CREATE INDEX tickets_assignee_idx ON tickets (assignee_id);

-- Every status change; SLA (paused while WAITING_FOR_CUSTOMER) will be derived from this later.
CREATE TABLE ticket_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES tickets (id),
    from_status VARCHAR(32),
    to_status   VARCHAR(32) NOT NULL,
    changed_by  VARCHAR(64) NOT NULL,
    note        TEXT,
    changed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ticket_status_history_ticket_idx ON ticket_status_history (ticket_id, changed_at);

CREATE TABLE ticket_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id),
    author_id  VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_comments_visibility_chk CHECK (visibility IN ('INTERNAL', 'EXTERNAL'))
);

CREATE INDEX ticket_comments_ticket_idx ON ticket_comments (ticket_id, created_at);

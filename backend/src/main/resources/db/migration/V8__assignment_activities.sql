-- Aşama 11: staff activity log (assignment, priority, category) and SLA due time for the work queue.

CREATE TABLE ticket_activities (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets (id),
    type       VARCHAR(32)  NOT NULL
        CONSTRAINT ticket_activities_type_chk CHECK (type IN ('ASSIGNEE_CHANGED', 'PRIORITY_CHANGED', 'CATEGORY_CHANGED')),
    old_value  VARCHAR(64),
    new_value  VARCHAR(64),
    note       TEXT,
    actor_id   VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_ticket_activities_ticket ON ticket_activities (ticket_id, created_at, id);

-- Moment the SLA target is reached while the clock runs; NULL while paused or stopped.
-- Kept in sync by Ticket whenever its SLA clock changes; used to order the work queue.
ALTER TABLE tickets ADD COLUMN sla_due_at TIMESTAMPTZ;

UPDATE tickets
SET sla_due_at = sla_running_since + make_interval(secs => sla_target_seconds - sla_elapsed_seconds)
WHERE sla_running_since IS NOT NULL;

CREATE INDEX idx_tickets_sla_due_at ON tickets (sla_due_at);
CREATE INDEX idx_tickets_assignee ON tickets (assignee_id);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_chk CHECK (type IN (
    'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED', 'ATTACHMENT_ADDED',
    'TICKET_RATED', 'TICKET_ASSIGNED', 'TICKET_UNASSIGNED', 'PRIORITY_CHANGED'));

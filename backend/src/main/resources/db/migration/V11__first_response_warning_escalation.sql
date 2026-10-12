-- Aşama 15a: first response SLA, early warning at 80 % of the resolution SLA, escalation on breach.

ALTER TABLE tickets
    -- First response: the first customer-visible reaction of staff (external comment or resolution).
    ADD COLUMN first_response_due_at             TIMESTAMPTZ,
    ADD COLUMN first_responded_at                TIMESTAMPTZ,
    ADD COLUMN first_response_breach_notified_at TIMESTAMPTZ,
    -- Moment 80 % of the resolution target is used while the clock runs; NULL while paused or stopped.
    ADD COLUMN sla_warn_at                       TIMESTAMPTZ,
    ADD COLUMN sla_warning_notified_at           TIMESTAMPTZ,
    ADD COLUMN escalated_at                      TIMESTAMPTZ;

CREATE INDEX idx_tickets_sla_warn_at ON tickets (sla_warn_at);
CREATE INDEX idx_tickets_first_response_due ON tickets (first_response_due_at) WHERE first_responded_at IS NULL;

-- Backfill with the default first-response targets.
UPDATE tickets
SET first_response_due_at = created_at + CASE priority
        WHEN 'CRITICAL' THEN INTERVAL '30 minutes'
        WHEN 'HIGH'     THEN INTERVAL '1 hour'
        WHEN 'MEDIUM'   THEN INTERVAL '4 hours'
        ELSE                 INTERVAL '8 hours'
    END;

-- Earliest external comment by staff, or the first resolution, counts as the first response.
UPDATE tickets t
SET first_responded_at = r.first_at
FROM (
    SELECT x.ticket_id, min(x.at) AS first_at
    FROM (
        SELECT c.ticket_id, c.created_at AS at
        FROM ticket_comments c
        JOIN app_users u ON u.id = c.author_id AND u.role IN ('AGENT', 'MANAGER')
        WHERE c.visibility = 'EXTERNAL'
        UNION ALL
        SELECT h.ticket_id, h.changed_at
        FROM ticket_status_history h
        WHERE h.to_status = 'RESOLVED'
    ) x
    GROUP BY x.ticket_id
) r
WHERE r.ticket_id = t.id;

UPDATE tickets
SET sla_warn_at = sla_running_since
        + make_interval(secs => GREATEST(0, (sla_target_seconds * 0.8) - sla_elapsed_seconds))
WHERE sla_running_since IS NOT NULL;

-- Do not flood existing users with notifications for situations that predate this feature.
UPDATE tickets SET first_response_breach_notified_at = now()
WHERE first_responded_at IS NULL AND first_response_due_at < now();
UPDATE tickets SET sla_warning_notified_at = now()
WHERE sla_breach_notified_at IS NOT NULL OR (sla_warn_at IS NOT NULL AND sla_warn_at <= now());
UPDATE tickets SET escalated_at = sla_breach_notified_at WHERE sla_breach_notified_at IS NOT NULL;

ALTER TABLE notifications DROP CONSTRAINT notifications_type_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_chk CHECK (type IN (
    'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED', 'ATTACHMENT_ADDED',
    'TICKET_RATED', 'TICKET_ASSIGNED', 'TICKET_UNASSIGNED', 'PRIORITY_CHANGED', 'SLA_WARNING',
    'FIRST_RESPONSE_BREACHED'));

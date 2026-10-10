-- Aşama 3: resolution-time SLA clock and auto-close lookup.
--
-- The SLA clock lives on the ticket row:
--   sla_target_seconds  target copied from the priority policy when the ticket is opened
--   sla_elapsed_seconds running time accumulated so far (clock stopped periods excluded)
--   sla_running_since   start of the current running period, NULL while paused/stopped

ALTER TABLE tickets
    ADD COLUMN sla_target_seconds  BIGINT,
    ADD COLUMN sla_elapsed_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sla_running_since   TIMESTAMPTZ;

-- Backfill rows created before this migration (development data only): targets follow the default
-- policy; elapsed time is approximated from the timestamps we have.
UPDATE tickets
SET sla_target_seconds = CASE priority
        WHEN 'CRITICAL' THEN 4 * 3600
        WHEN 'HIGH'     THEN 8 * 3600
        WHEN 'MEDIUM'   THEN 24 * 3600
        ELSE                 72 * 3600
    END;

UPDATE tickets
SET sla_running_since = CASE WHEN status IN ('NEW', 'IN_PROGRESS') THEN created_at END,
    sla_elapsed_seconds = CASE
        WHEN status IN ('NEW', 'IN_PROGRESS') THEN 0
        WHEN status = 'WAITING_FOR_CUSTOMER' THEN GREATEST(0, EXTRACT(EPOCH FROM (updated_at - created_at)))::BIGINT
        ELSE GREATEST(0, EXTRACT(EPOCH FROM (COALESCE(resolved_at, closed_at, updated_at) - created_at)))::BIGINT
    END;

ALTER TABLE tickets ALTER COLUMN sla_target_seconds SET NOT NULL;
ALTER TABLE tickets ADD CONSTRAINT tickets_sla_target_chk CHECK (sla_target_seconds > 0);
ALTER TABLE tickets ADD CONSTRAINT tickets_sla_elapsed_chk CHECK (sla_elapsed_seconds >= 0);

-- Auto-close polls RESOLVED tickets whose resolved_at is old enough.
CREATE INDEX tickets_status_resolved_at_idx ON tickets (status, resolved_at);

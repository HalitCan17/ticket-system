-- Aşama 17: service catalog. Managers maintain catalog items (with a small form); anybody requests one.
-- Requests that need approval wait for a manager in the jBPM process "serviceRequest"; an approved (or approval-free)
-- request becomes an ordinary ticket. The request row is what the UI reads; the process only orchestrates.

CREATE TABLE catalog_items (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(16)  NOT NULL
        CONSTRAINT catalog_items_category_chk CHECK (category IN ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER')),
    priority          VARCHAR(16)  NOT NULL
        CONSTRAINT catalog_items_priority_chk CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    requires_approval BOOLEAN      NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- [{"key": "model", "label": "Model", "type": "SELECT", "options": ["..."], "required": true}, ...]
    fields            JSONB        NOT NULL DEFAULT '[]',
    created_by        VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_catalog_items_name ON catalog_items (lower(name));

CREATE TABLE service_requests (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             BIGINT       NOT NULL REFERENCES catalog_items (id),
    requester_id        VARCHAR(64)  NOT NULL,
    -- Field key -> answer, as validated against the item's fields when the request was made.
    answers             JSONB        NOT NULL DEFAULT '{}',
    status              VARCHAR(20)  NOT NULL
        CONSTRAINT service_requests_status_chk
            CHECK (status IN ('PENDING_APPROVAL', 'FULFILLING', 'REJECTED', 'CANCELLED')),
    process_instance_id VARCHAR(36),
    approver_id         VARCHAR(64),
    decision_comment    VARCHAR(1000),
    decided_at          TIMESTAMPTZ,
    ticket_id           BIGINT REFERENCES tickets (id),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_service_requests_requester ON service_requests (requester_id, id DESC);
CREATE INDEX idx_service_requests_status ON service_requests (status, id DESC);

-- Notifications may now point at a service request instead of a ticket (exactly one of the two).
ALTER TABLE notifications ALTER COLUMN ticket_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN service_request_id BIGINT REFERENCES service_requests (id) ON DELETE CASCADE;
ALTER TABLE notifications ADD CONSTRAINT notifications_subject_chk
    CHECK ((ticket_id IS NULL) <> (service_request_id IS NULL));

ALTER TABLE notifications DROP CONSTRAINT notifications_type_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_chk CHECK (type IN (
    'STATUS_CHANGED', 'AUTO_CLOSED', 'COMMENT_ADDED', 'INTERNAL_COMMENT_ADDED', 'SLA_BREACHED', 'ATTACHMENT_ADDED',
    'TICKET_RATED', 'TICKET_ASSIGNED', 'TICKET_UNASSIGNED', 'PRIORITY_CHANGED', 'SLA_WARNING',
    'FIRST_RESPONSE_BREACHED', 'APPROVAL_REQUESTED', 'REQUEST_APPROVED', 'REQUEST_REJECTED'));

INSERT INTO catalog_items (name, description, category, priority, requires_approval, fields,
                           created_by, created_at, updated_by, updated_at) VALUES
    ('Yeni dizüstü bilgisayar',
     'Yeni ya da yenilenmiş bir dizüstü bilgisayar. Yönetici onayından sonra destek ekibi hazırlayıp teslim eder.',
     'HARDWARE', 'MEDIUM', TRUE,
     '[{"key": "model", "label": "Model", "type": "SELECT", "options": ["Standart (14 inç)", "Geliştirici (16 inç, 32 GB)"], "required": true},
       {"key": "reason", "label": "Gerekçe", "type": "TEXTAREA", "options": [], "required": true}]',
     'system', now(), 'system', now()),
    ('Yazılım lisansı',
     'Kurumun lisansladığı bir yazılım için kullanıcı lisansı.',
     'SOFTWARE', 'LOW', TRUE,
     '[{"key": "software", "label": "Yazılım", "type": "TEXT", "options": [], "required": true},
       {"key": "seats", "label": "Kullanıcı sayısı", "type": "NUMBER", "options": [], "required": true}]',
     'system', now(), 'system', now()),
    ('VPN erişimi',
     'Kurum ağına uzaktan bağlanmak için VPN hesabı.',
     'NETWORK', 'MEDIUM', FALSE,
     '[{"key": "device", "label": "Cihaz", "type": "SELECT", "options": ["Dizüstü", "Masaüstü", "Telefon"], "required": true}]',
     'system', now(), 'system', now()),
    ('Ortak klasör erişimi',
     'Bir ekip klasörüne okuma ya da yazma yetkisi.',
     'ACCESS', 'LOW', FALSE,
     '[{"key": "folder", "label": "Klasör yolu", "type": "TEXT", "options": [], "required": true},
       {"key": "access", "label": "Yetki", "type": "SELECT", "options": ["Okuma", "Okuma ve yazma"], "required": true}]',
     'system', now(), 'system', now());

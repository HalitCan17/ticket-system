-- Aşama 14: support groups, category routing and automatic assignment inside a group.

CREATE TABLE support_groups (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(80)  NOT NULL,
    description      TEXT,
    assignment_mode  VARCHAR(16)  NOT NULL DEFAULT 'NONE'
        CONSTRAINT support_groups_mode_chk CHECK (assignment_mode IN ('NONE', 'ROUND_ROBIN', 'LEAST_LOADED')),
    -- Round-robin cursor: the member who got the last automatic assignment.
    last_assigned_id VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_support_groups_name ON support_groups (lower(name));

CREATE TABLE support_group_members (
    group_id BIGINT      NOT NULL REFERENCES support_groups (id) ON DELETE CASCADE,
    user_id  VARCHAR(64) NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_support_group_members_user ON support_group_members (user_id);

-- Each category goes to at most one group.
CREATE TABLE category_routes (
    category VARCHAR(16) PRIMARY KEY
        CONSTRAINT category_routes_category_chk CHECK (category IN ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER')),
    group_id BIGINT      NOT NULL REFERENCES support_groups (id) ON DELETE CASCADE
);

ALTER TABLE tickets ADD COLUMN group_id BIGINT REFERENCES support_groups (id) ON DELETE SET NULL;
CREATE INDEX idx_tickets_group ON tickets (group_id);

ALTER TABLE ticket_activities DROP CONSTRAINT ticket_activities_type_chk;
ALTER TABLE ticket_activities ADD CONSTRAINT ticket_activities_type_chk
    CHECK (type IN ('ASSIGNEE_CHANGED', 'PRIORITY_CHANGED', 'CATEGORY_CHANGED', 'GROUP_CHANGED'));

-- Starter groups, one per category. They have no members yet: Keycloak user ids are only known after sign-in,
-- so managers add members on the Groups page. Existing tickets join the group of their category.
INSERT INTO support_groups (name, description, created_at, updated_at) VALUES
    ('Donanım ekibi', 'Bilgisayar, yazıcı ve çevre birimleri', now(), now()),
    ('Uygulama desteği', 'Kurumsal yazılımlar ve lisanslar', now(), now()),
    ('Ağ ekibi', 'İnternet, VPN ve kablosuz ağ', now(), now()),
    ('Hesap ve erişim', 'Kullanıcı hesapları, şifreler ve yetkiler', now(), now()),
    ('Genel destek', 'Diğer bütün talepler', now(), now());

INSERT INTO category_routes (category, group_id)
SELECT c.category, g.id
FROM (VALUES ('HARDWARE', 'Donanım ekibi'),
             ('SOFTWARE', 'Uygulama desteği'),
             ('NETWORK', 'Ağ ekibi'),
             ('ACCESS', 'Hesap ve erişim'),
             ('OTHER', 'Genel destek')) AS c (category, group_name)
JOIN support_groups g ON g.name = c.group_name;

UPDATE tickets t SET group_id = r.group_id FROM category_routes r WHERE r.category = t.category;

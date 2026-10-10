-- Aşama 4: local directory of users seen in access tokens, used to show names instead of ids.
-- Keycloak stays the source of truth; rows are refreshed from token claims (sub, preferred_username,
-- name, email) whenever they change.
CREATE TABLE app_users (
    id           VARCHAR(64) PRIMARY KEY,
    username     VARCHAR(255),
    display_name VARCHAR(255),
    email        VARCHAR(320),
    updated_at   TIMESTAMPTZ NOT NULL
);

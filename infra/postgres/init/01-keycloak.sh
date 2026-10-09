#!/bin/sh
# Creates Keycloak's own database and user next to the application database.
# Runs only when the Postgres volume is empty (docker-entrypoint-initdb.d semantics).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE USER keycloak WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL

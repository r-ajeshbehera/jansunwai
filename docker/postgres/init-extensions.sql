-- Runs once when the data volume is created. Flyway's V1 also does CREATE EXTENSION IF NOT EXISTS,
-- but creating extensions needs superuser rights, so we do it here as the postgres user.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

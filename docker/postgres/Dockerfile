# PostgreSQL 16 + pgvector (semantic duplicate search) + PostGIS 3 (maps). Both extensions are free and open source.
# Base: the official pgvector image (Debian bookworm); PostGIS comes from the PostgreSQL apt repository it already uses.
FROM pgvector/pgvector:pg16
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-16-postgis-3 \
 && rm -rf /var/lib/apt/lists/*
COPY init-extensions.sql /docker-entrypoint-initdb.d/20-extensions.sql

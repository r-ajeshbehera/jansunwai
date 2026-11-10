# Database

PostgreSQL 16 with **PostGIS** (maps, ward detection, distance, clustering) and **pgvector** (semantic duplicate search).
The image is built from `docker/postgres/Dockerfile`.

- The schema is owned by **Flyway** (`spring.jpa.hibernate.ddl-auto=none`):
  - `backend/src/main/resources/db/migration/V1__init_schema.sql` – tables, indexes, constraints (`schema.sql` here is a copy for reading)
  - `V2__reference_data.sql` – departments and default SLA policies (sample values)
  - `db/demo/V3__demo_data.sql` – demo wards, users and 20 complaints (only with the `dev` profile)
- ER diagram and design notes: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md#4-data-model)

Notable techniques: generated `geography` column with a GiST index, `VECTOR(768)` with an HNSW cosine index,
a sequence for tracking IDs, a partial unique index (one active assignment per complaint), a unique key that makes
escalations idempotent, and CHECK constraints that repeat workflow invariants.

-- =====================================================================================
-- JanSunwai schema (PostgreSQL 16 + PostGIS 3 + pgvector)
-- Rules that must never break are enforced HERE, not only in Java:
--   CHECK constraints, partial unique indexes (one active assignment), unique keys (idempotent escalations).
-- =====================================================================================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- reference data ----------------------------------------------------------
CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,          -- PUBLIC_WORKS, WATER_SUPPLY ... (used by AI output)
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE wards (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    boundary    GEOMETRY(Polygon, 4326),               -- used to detect the ward from a GPS point
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wards_boundary ON wards USING GIST (boundary);

-- ---------- users ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    full_name      VARCHAR(120) NOT NULL,
    email          VARCHAR(160) NOT NULL,
    phone          VARCHAR(15),
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    department_id  BIGINT REFERENCES departments (id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_role CHECK (role IN ('CITIZEN', 'FIELD_OFFICER', 'DEPARTMENT_OFFICER', 'ADMIN')),
    -- officers always belong to exactly one department; citizens and admins never do
    CONSTRAINT ck_users_department CHECK ((role IN ('FIELD_OFFICER', 'DEPARTMENT_OFFICER')) = (department_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE INDEX idx_users_department_role ON users (department_id, role);

-- ---------- complaints ----------------------------------------------------------------
CREATE SEQUENCE complaint_tracking_seq START 1001;

CREATE TABLE complaints (
    id                     BIGSERIAL PRIMARY KEY,
    tracking_id            VARCHAR(20)   NOT NULL UNIQUE,       -- JS-2026-001001, shown to citizens
    citizen_id             BIGINT        NOT NULL REFERENCES users (id),
    title                  VARCHAR(150)  NOT NULL,
    description            TEXT          NOT NULL,              -- ORIGINAL text, never overwritten
    language               VARCHAR(5)    NOT NULL,              -- en / hi / or (selected or detected)
    ward_id                BIGINT REFERENCES wards (id),
    latitude               NUMERIC(9, 6),
    longitude              NUMERIC(9, 6),
    location               GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (CASE WHEN latitude IS NULL THEN NULL
            ELSE ST_SetSRID(ST_MakePoint(longitude::float8, latitude::float8), 4326)::geography END) STORED,
    landmark               VARCHAR(200),                        -- private: never in public APIs
    -- current routing/classification (AI suggestion first, then human-approved)
    category               VARCHAR(30),
    department_id          BIGINT REFERENCES departments (id),
    priority               VARCHAR(10),
    classification_source  VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    classified_by          BIGINT REFERENCES users (id),
    classified_at          TIMESTAMPTZ,
    status                 VARCHAR(25)   NOT NULL DEFAULT 'SUBMITTED',
    ai_attempts            INT           NOT NULL DEFAULT 0,
    duplicate_group_id     BIGINT,
    accepted_at            TIMESTAMPTZ,
    sla_due_at             TIMESTAMPTZ,
    sla_warned_at          TIMESTAMPTZ,
    escalation_level       INT           NOT NULL DEFAULT 0,
    resolved_at            TIMESTAMPTZ,
    closed_at              TIMESTAMPTZ,
    rejection_reason       VARCHAR(300),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,      -- JPA @Version (optimistic locking)
    CONSTRAINT ck_c_status CHECK (status IN ('SUBMITTED', 'AI_ANALYSIS_PENDING', 'UNDER_REVIEW', 'APPROVED', 'ASSIGNED',
        'IN_PROGRESS', 'RESOLUTION_SUBMITTED', 'RESOLVED', 'CLOSED', 'REJECTED', 'REOPENED')),
    CONSTRAINT ck_c_language CHECK (language IN ('en', 'hi', 'or')),
    CONSTRAINT ck_c_priority CHECK (priority IS NULL OR priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_c_category CHECK (category IS NULL OR category IN ('ROAD_DAMAGE', 'WATER_SUPPLY', 'DRAINAGE',
        'GARBAGE', 'STREETLIGHT', 'SANITATION', 'OTHER')),
    CONSTRAINT ck_c_source CHECK (classification_source IN ('NONE', 'AI_SUGGESTED', 'OFFICER_CONFIRMED', 'OFFICER_CORRECTED')),
    CONSTRAINT ck_c_latlng CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_c_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_c_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_c_rejected CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    -- from APPROVED onwards a complaint must have a human-approved classification and an SLA deadline
    CONSTRAINT ck_c_approved CHECK (status IN ('SUBMITTED', 'AI_ANALYSIS_PENDING', 'UNDER_REVIEW', 'REJECTED')
        OR (category IS NOT NULL AND department_id IS NOT NULL AND priority IS NOT NULL
            AND classification_source IN ('OFFICER_CONFIRMED', 'OFFICER_CORRECTED') AND sla_due_at IS NOT NULL)),
    CONSTRAINT ck_c_escalation CHECK (escalation_level BETWEEN 0 AND 2)
);
CREATE INDEX idx_c_citizen      ON complaints (citizen_id, created_at DESC);
CREATE INDEX idx_c_dept_status  ON complaints (department_id, status);
CREATE INDEX idx_c_ward_status  ON complaints (ward_id, status);
CREATE INDEX idx_c_status_due   ON complaints (status, sla_due_at);
CREATE INDEX idx_c_location     ON complaints USING GIST (location);

-- ---------- attachments ---------------------------------------------------------------
CREATE TABLE complaint_attachments (
    id             BIGSERIAL PRIMARY KEY,
    complaint_id   BIGINT       NOT NULL REFERENCES complaints (id),
    kind           VARCHAR(12)  NOT NULL,
    stored_name    VARCHAR(80)  NOT NULL UNIQUE,     -- random UUID file name on disk
    original_name  VARCHAR(150) NOT NULL,
    content_type   VARCHAR(30)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_by    BIGINT       NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_att_kind CHECK (kind IN ('EVIDENCE', 'RESOLUTION')),
    CONSTRAINT ck_att_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_att_size CHECK (size_bytes BETWEEN 1 AND 5242880)
);
CREATE INDEX idx_att_complaint ON complaint_attachments (complaint_id);

-- ---------- AI results (stored separately from the citizen's complaint) -----------------
CREATE TABLE complaint_ai_analyses (
    id                 BIGSERIAL PRIMARY KEY,
    complaint_id       BIGINT        NOT NULL REFERENCES complaints (id),
    provider           VARCHAR(30)   NOT NULL,
    model              VARCHAR(60)   NOT NULL,
    status             VARCHAR(10)   NOT NULL,
    category           VARCHAR(30),
    department_code    VARCHAR(30),
    priority           VARCHAR(10),
    detected_language  VARCHAR(5),
    summary_en         VARCHAR(400),
    translation_en     TEXT,
    suggested_actions  VARCHAR(600),
    confidence         NUMERIC(3, 2),
    needs_review       BOOLEAN       NOT NULL DEFAULT TRUE,
    error_code         VARCHAR(40),
    latency_ms         INT,
    input_tokens       INT,
    output_tokens      INT,
    cost_usd           NUMERIC(10, 6) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_conf CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_ai_ok CHECK (status = 'FAILED' OR (category IS NOT NULL AND priority IS NOT NULL AND confidence IS NOT NULL))
);
CREATE INDEX idx_ai_complaint ON complaint_ai_analyses (complaint_id, created_at DESC);

-- 768 dimensions: Gemini text-embedding-004, Ollama nomic-embed-text and the offline mock all produce 768 values.
CREATE TABLE complaint_embeddings (
    complaint_id  BIGINT PRIMARY KEY REFERENCES complaints (id),
    embedding     VECTOR(768)  NOT NULL,
    model         VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_emb_hnsw ON complaint_embeddings USING hnsw (embedding vector_cosine_ops);

-- ---------- duplicates (AI suggests, a human decides) -----------------------------------
CREATE TABLE duplicate_groups (
    id                   BIGSERIAL PRIMARY KEY,
    master_complaint_id  BIGINT       NOT NULL UNIQUE REFERENCES complaints (id),
    created_by           BIGINT       NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
ALTER TABLE complaints ADD CONSTRAINT fk_c_group FOREIGN KEY (duplicate_group_id) REFERENCES duplicate_groups (id);

CREATE TABLE duplicate_candidates (
    id              BIGSERIAL PRIMARY KEY,
    complaint_id    BIGINT        NOT NULL REFERENCES complaints (id),   -- the newer complaint
    candidate_id    BIGINT        NOT NULL REFERENCES complaints (id),   -- the older, possibly same issue
    semantic_score  NUMERIC(4, 3) NOT NULL,
    distance_m      INT,
    total_score     NUMERIC(4, 3) NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    decided_by      BIGINT REFERENCES users (id),
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_dc_pair UNIQUE (complaint_id, candidate_id),
    CONSTRAINT ck_dc_self CHECK (complaint_id <> candidate_id),
    CONSTRAINT ck_dc_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_dc_decided CHECK ((status = 'PENDING') = (decided_by IS NULL))
);
CREATE INDEX idx_dc_status ON duplicate_candidates (status, complaint_id);

-- ---------- assignment, history, remarks ------------------------------------------------
CREATE TABLE complaint_assignments (
    id                BIGSERIAL PRIMARY KEY,
    complaint_id      BIGINT       NOT NULL REFERENCES complaints (id),
    field_officer_id  BIGINT       NOT NULL REFERENCES users (id),
    assigned_by       BIGINT       NOT NULL REFERENCES users (id),
    note              VARCHAR(300),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    assigned_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    CONSTRAINT ck_asg_end CHECK (active = (ended_at IS NULL))
);
-- PostgreSQL partial unique index: at most ONE active assignment per complaint
CREATE UNIQUE INDEX uq_asg_one_active ON complaint_assignments (complaint_id) WHERE active;
CREATE INDEX idx_asg_officer ON complaint_assignments (field_officer_id, active);

CREATE TABLE complaint_status_history (
    id            BIGSERIAL PRIMARY KEY,
    complaint_id  BIGINT       NOT NULL REFERENCES complaints (id),
    from_status   VARCHAR(25),
    to_status     VARCHAR(25)  NOT NULL,
    note          VARCHAR(500),
    public_note   BOOLEAN      NOT NULL DEFAULT TRUE,
    changed_by    BIGINT REFERENCES users (id),     -- NULL = system (AI pipeline, scheduler)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_hist_complaint ON complaint_status_history (complaint_id, created_at);

CREATE TABLE complaint_remarks (
    id            BIGSERIAL PRIMARY KEY,
    complaint_id  BIGINT       NOT NULL REFERENCES complaints (id),
    author_id     BIGINT       NOT NULL REFERENCES users (id),
    visibility    VARCHAR(10)  NOT NULL,
    info_request  BOOLEAN      NOT NULL DEFAULT FALSE,
    text          VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_rem_vis CHECK (visibility IN ('INTERNAL', 'PUBLIC'))
);
CREATE INDEX idx_rem_complaint ON complaint_remarks (complaint_id, created_at);

-- ---------- SLA and escalation ----------------------------------------------------------
CREATE TABLE sla_configurations (
    id                BIGSERIAL PRIMARY KEY,
    department_id     BIGINT REFERENCES departments (id),   -- NULL = default policy for all departments
    priority          VARCHAR(10)  NOT NULL,
    resolution_hours  INT          NOT NULL,
    warning_percent   INT          NOT NULL DEFAULT 75,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sla_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_sla_hours CHECK (resolution_hours BETWEEN 1 AND 2160),
    CONSTRAINT ck_sla_warn CHECK (warning_percent BETWEEN 10 AND 95)
);
-- one policy per (department or default, priority)
CREATE UNIQUE INDEX uq_sla_dept_priority ON sla_configurations (COALESCE(department_id, 0), priority);

CREATE TABLE escalation_history (
    id             BIGSERIAL PRIMARY KEY,
    complaint_id   BIGINT       NOT NULL REFERENCES complaints (id),
    level          INT          NOT NULL,
    reason         VARCHAR(200) NOT NULL,
    due_at         TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_esc_level UNIQUE (complaint_id, level),          -- idempotent scheduler: one row per level
    CONSTRAINT ck_esc_level CHECK (level BETWEEN 1 AND 2)
);

-- ---------- notifications, audit, summaries, tokens -------------------------------------
CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    complaint_id  BIGINT REFERENCES complaints (id),
    type          VARCHAR(30)  NOT NULL,
    message       VARCHAR(500) NOT NULL,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user ON notifications (user_id, read_at, created_at DESC);

CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT REFERENCES users (id),
    action       VARCHAR(60)  NOT NULL,
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    BIGINT,
    details      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_time ON audit_logs (created_at DESC);

CREATE TABLE ward_summaries (
    id          BIGSERIAL PRIMARY KEY,
    ward_id     BIGINT       NOT NULL REFERENCES wards (id),
    week_start  DATE         NOT NULL,
    facts_json  JSONB        NOT NULL,     -- numbers computed by SQL (observed facts)
    narrative   TEXT         NOT NULL,     -- AI-written interpretation, labelled as such in the UI
    provider    VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ws_week UNIQUE (ward_id, week_start)
);

CREATE TABLE revoked_tokens (
    jti         VARCHAR(36) PRIMARY KEY,
    expires_at  TIMESTAMPTZ NOT NULL
);

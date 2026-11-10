# Interview preparation — JanSunwai

## 30-second pitch

"JanSunwai is a civic grievance system I built with Java 21, Spring Boot and PostgreSQL. Citizens report problems like
potholes or water supply in English, Hindi or Odia. AI suggests the category, department and priority and writes an
English summary, but an officer always confirms. Using pgvector and PostGIS it finds duplicate complaints even across
languages, for example a Hindi and an English complaint about the same pothole 45 metres apart. Accepted complaints get a
deadline, and a scheduler escalates overdue ones exactly once, even if it runs on several servers. It has 100 automated
tests, including 40 integration tests on a real PostgreSQL with Testcontainers, and runs with one Docker Compose command."

## 2-minute walkthrough (follow the screens)

1. **Citizen** writes in Hindi, pins the location → gets tracking ID immediately (`201`), ward detected with `ST_Contains`.
2. **After commit**, an async listener runs the AI pipeline: short transaction → AI call outside the transaction with a
   20 s timeout → validate → short transaction. If AI fails 3 times → manual review. The complaint is never lost.
3. **Embedding** stored in pgvector; similar older complaints within 500 m are scored → **duplicate suggestion**.
4. **Officer** sees the AI suggestion with confidence, corrects if needed, approves → SLA deadline set from configurable rules.
5. Assigns a **field officer** (optimistic locking: two officers at once → one wins, the other gets 409).
6. Field officer starts work, must upload a **photo** (magic-byte checked) before submitting the resolution.
7. Officer approves → linked duplicates resolve too → citizen confirms or reopens within 7 days.
8. **Scheduler** every 5 min: warning at 75 %, L1 at deadline, L2 after 24 h — idempotent by SQL.
9. **Dashboard** from SQL aggregates, PostGIS hotspots (`ST_ClusterDBSCAN`), weekly ward report where AI only phrases SQL facts.

## Likely questions

**Why not let the AI route complaints automatically?**
Wrong routing wastes days and nobody is accountable for a model's mistake. The AI saves the officer reading time
(summary, translation, suggestion); the decision stays with a person. Below 0.60 confidence the suggestion is not even
pre-selected for routing. The database enforces it: an APPROVED complaint must have an officer-confirmed classification (CHECK constraint).

**How do you stop the AI from returning garbage?**
`AiOutputValidator`: category and priority must be exact enum values, the department must exist, confidence must be in
[0,1], text is trimmed and length-limited. Anything else → `INVALID_OUTPUT`, retried, then manual review. Tested with a
Mockito provider that returns "FLYING_SAUCER" / confidence 7.0.

**What happens if Gemini is down or slow?**
`AiGateway` runs the call on its own thread pool with a hard timeout and retries retryable errors with backoff. The
complaint was already committed before the AI started, so the citizen is not affected. `AiRetryJob` picks it up again;
after 3 attempts it goes to manual review and admins are notified.

**Why do the AI call outside the transaction?**
A transaction holds a database connection. A 10-second AI call inside it would drain the connection pool under load. So
the pipeline is: short transaction (mark pending) → AI call (no transaction) → short transaction (save result, re-check status).

**How does duplicate detection work across languages?**
Each complaint is embedded into a 768-dimension vector; meaning, not words, decides closeness. pgvector's `<=>` gives
cosine distance with an HNSW index. I combine it with PostGIS distance and category:
`0.60·semantic + 0.25·closeness + 0.15·category`. A known distance above 500 m means a different place. Suggestions
are stored as PENDING; an officer links them. Each citizen keeps their tracking ID and gets updates when the master is resolved.

**Why one PostgreSQL and not a separate vector database?**
Complaints, vectors and locations change together; one database gives one transaction, one backup, joins between
similarity and distance in one query, and no extra paid service. pgvector with HNSW is fast enough for city scale.

**How is escalation "exactly once"?**
Not with Java locks — those do not work across servers. The database decides: `INSERT … ON CONFLICT (complaint_id, level)
DO NOTHING` returns 1 only for the first run, and only that run sends the notification. The warning uses
`UPDATE … WHERE sla_warned_at IS NULL`. I test it with 4 threads calling the job at the same moment.

**Why is ESCALATED not a status?**
An overdue complaint is still IN_PROGRESS. If ESCALATED were a state, you would lose where the work actually is and need
extra transitions back. A level flag (0–2) keeps the state machine small and honest.

**How do you handle two officers assigning at the same time?**
`@Version` optimistic locking on the complaint: both read version 5, the first update makes it 6, the second update finds
no row with version 5 → `ObjectOptimisticLockingFailureException` → 409. A partial unique index
(`one active assignment per complaint`) is the second safety net. Tested 3 rounds with a `CountDownLatch` start gate.

**How do you generate unique tracking IDs under load?**
A PostgreSQL sequence (`nextval` never returns the same value twice, no locks). `max(id)+1` would race. Tested with 20 parallel submissions.

**Security?**
Stateless JWT (HS384) with a `jti`; logout stores the `jti` in `revoked_tokens` until expiry. Roles with
`@PreAuthorize` plus object-level checks in `AccessPolicy` (a citizen gets 404, not 403, for someone else's complaint —
no existence leak). BCrypt passwords, rate limits per IP, uploads checked by magic bytes and stored with random names,
the public tracking page shows no personal data, PII masked before text goes to an AI vendor, no secrets in the code
(prod profile refuses to start without `JWT_SECRET`).

**Why Testcontainers instead of H2?**
The features under test — pgvector, PostGIS, `ON CONFLICT`, partial indexes, CHECK constraints — only exist in
PostgreSQL. The tests found real bugs, e.g. an implicit inner join in a JPQL query that made the SLA monitor skip
every complaint without a duplicate group.

**What would you improve next?**
Redis-based rate limiting for several instances; SMS/WhatsApp notifications through a provider; photo storage on S3;
evaluate a real embedding model and tune the threshold on labelled data; officer workload balancing for assignment.

## Numbers to remember

100 tests (60 unit, 40 integration) · 74.5 % line coverage · 52 REST operations · 11 workflow states · 4 roles ·
3 languages · 18 tables · duplicate radius 500 m · SLA HIGH 24 h / MEDIUM 72 h / LOW 168 h (sample values).

# Testing

## Results (measured on the final code)

```
mvn verify
  Unit tests (surefire)        Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
  Integration tests (failsafe) Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
JaCoCo (unit + integration): 74.5 % lines (1685 of 2262), 57.5 % branches, 72.9 % instructions
```

Not covered by automated tests (and why): `SpringAiProvider` needs a real model/API key (it is exercised manually and
through the same `AiGateway`/validator path as the mock); the four `@Scheduled` wrapper classes are disabled in tests,
while the services they call (`EscalationService.runOnce`, `AiPipelineService.process`, `WardSummaryService`) are tested directly.

## Strategy

| Level | Tools | What it proves |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | pure rules: state machine, duplicate scoring, AI validation, timeouts and retries, PII masking, language detection, JWT |
| Integration | Spring Boot on a random port + real HTTP + Testcontainers PostgreSQL 16 / PostGIS / pgvector | the real thing: security filters, SQL, PostGIS, pgvector, transactions, async AI pipeline |
| Concurrency | `CountDownLatch` start gate, thread pools, real HTTP | races are handled by the database, not by luck |
| End-to-end smoke | Python script against the running stack (also through Nginx in Docker) | 20 checks of the full journey |
| UI smoke | Playwright, every page per role | no console/page errors, screenshots |

Why a real database and not H2: the core features (vectors, geography, `ON CONFLICT`, partial indexes, CHECK
constraints) only exist in PostgreSQL. An in-memory database would test something else.

## Integration tests and the edge cases they cover

**ComplaintWorkflowIT** — full lifecycle submit → AI → approve → assign → start → photo → resolution → resolve → close
(timeline order, citizen sees only public steps, 3 citizen notifications); citizen cannot approve/assign (403); invalid
transitions (APPROVED → RESOLVED = 409, forcing ASSIGNED via status endpoint = 422, second review = 409); resolution
without photo (422 `EVIDENCE_REQUIRED`), send back needs a note, reopen needs a reason, reassignment keeps exactly one
active assignment; rejection needs a reason, is final, public page shows only "Not accepted"; officer can only accept
for own department but can reroute; validation errors (empty title, latitude 123, language "fr", unknown id); complaint
without location.

**DuplicateDetectionIT** — a **Hindi** complaint about the same pothole as an English one, 45 m away, is suggested;
nothing changes until an officer approves; approving twice = 409; working the master resolves the linked complaint;
rejected suggestion leaves both independent; same place but different problem, or same text 1.1 km away, is not a
duplicate; citizens cannot see or decide suggestions.

**EscalationIT** — overdue complaint escalated once even after 3 runs (status unchanged: escalation is a flag);
30 h overdue → level 2 + admin notified; **4 parallel runs** still create one row per level and one notification;
deadline warning sent once; complaints not under SLA never escalated; resolved complaint auto-closed after 7 days.

**ConcurrencyIT** — officer and admin assign the same complaint at the same instant (repeated 3×): exactly one 200,
the other 409, one active assignment row; 20 simultaneous submissions → 20 unique tracking IDs; double-click on
"start work" → one status change.

**AiFailureIT** (AI provider replaced with a Mockito mock) — provider down: complaint saved (201), 3 failed attempts
recorded, then manual review with admin notification, officer can still approve; invalid AI output (unknown category,
department, confidence 7.0) refused; low-confidence suggestion shown but not routed.

**SecurityPrivacyIT** — 401 without/with garbage token; same message for wrong password and unknown e-mail; registration
always creates a CITIZEN even if the body says ADMIN, duplicate e-mail 422, weak password/bad phone 400; logout revokes
the token; other citizens and unassigned field officers get 404 (no existence leak); citizen view hides internal data;
public tracking contains no phone, coordinates, description or citizen name; admin endpoints are admin-only; uploads
checked by magic bytes (fake PNG rejected) and kind decided by role; SQL-injection text in search is just text.

**GeoDashboardIT** — ward detected from GPS (and none outside wards); ward shapes are public GeoJSON; nearby summary;
dashboard numbers match the database and are scoped per department; weekly report narrative contains exactly the
computed facts and generation is admin-only; AI usage tracked.

Bugs these tests found during development (and fixed): AI pipeline crashed (NPE) when the final AI attempt failed;
dashboard totals failed on a `BigDecimal` cast; SLA queries silently skipped complaints without a duplicate group
(implicit inner join); duplicate job ordering missed older complaints.

## How to run

```bash
# once: the database image used by Testcontainers
docker build -t jansunwai-postgres:16 docker/postgres

cd backend
mvn test        # unit tests only (no Docker needed)
mvn verify      # unit + integration + JaCoCo report in target/site/jacoco/index.html
```

Without Docker, point the integration tests at an **empty** PostgreSQL database that has PostGIS and pgvector:

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5432/jansunwai_test TEST_DB_USERNAME=postgres TEST_DB_PASSWORD=secret mvn verify
```

The test profile (`application-test.yml`) disables the schedulers, uses the mock AI and raises rate limits.
CI (`.github/workflows/ci.yml`) builds the database image and runs `mvn verify` on every push and pull request.

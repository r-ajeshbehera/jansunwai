# Resume lines (pick 3–4)

**JanSunwai – AI-Powered Multilingual Civic Grievance System** | Java 21, Spring Boot 3, Spring AI, PostgreSQL (PostGIS, pgvector), React, Docker

- Built a role-based grievance platform (citizen, field officer, department officer, admin) with an 11-state workflow,
  JWT security and 52 REST endpoints; complaints accepted in English, Hindi and Odia.
- Integrated Spring AI behind a provider interface (Gemini / Ollama / offline mock) for classification, translation and
  summaries, with timeouts, retries, strict output validation and PII masking; officers confirm every AI suggestion.
- Implemented cross-language duplicate detection using pgvector embeddings combined with PostGIS distance, plus ward
  detection, heatmaps and DBSCAN hotspots on OpenStreetMap.
- Designed an idempotent SLA escalation scheduler (ON CONFLICT DO NOTHING) and optimistic locking for concurrent
  assignment; verified with 100 automated tests (40 Testcontainers integration tests, 74.5% line coverage) and GitHub Actions CI.

**Testing & results line (for a "Testing" sub-bullet):** 100 JUnit/Mockito/Testcontainers tests passing, including
concurrency tests (parallel assignment, 20 simultaneous submissions, 4 parallel scheduler runs) and AI-failure tests.

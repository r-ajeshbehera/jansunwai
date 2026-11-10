# Deployment

## Local with Docker Compose

```bash
cp .env.example .env          # optional, everything has a local default
docker compose up --build
```

| Service | Image | Port | Notes |
|---|---|---|---|
| db | `docker/postgres` (pgvector:pg16 + PostGIS 3) | 127.0.0.1:5432 | data in volume `db-data` |
| backend | `backend/Dockerfile` (Maven build → JRE 21, non-root) | 127.0.0.1:8080 | health check `/actuator/health`, uploads in volume `uploads` |
| frontend | `frontend/Dockerfile` (Vite build → Nginx) | 3000 | serves the SPA and proxies `/api` to the backend |
| ollama | `ollama/ollama` | 127.0.0.1:11434 | only with `--profile ollama` |

Reset everything (including demo data): `docker compose down -v`.

The `dev` profile (default in compose) loads **demo data**. For an empty system set `SPRING_PROFILES_ACTIVE=prod`
and create the first admin directly in the database (or temporarily via the dev profile).

### Demo accounts (password `Demo@1234`)

| Role | E-mail |
|---|---|
| Admin | admin@jansunwai.demo |
| Department officers | roads.officer@, water.officer@, drainage.officer@, sanitation.officer@, electrical.officer@ (…@jansunwai.demo) |
| Field officers | roads.field@, roads.field2@, water.field@, drainage.field@, sanitation.field@, electrical.field@ |
| Citizens | citizen1@, citizen2@, citizen3@ |

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | – | `dev` (demo data, debug logs) or `prod` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost / jansunwai / change-me | database (no password default in `prod`) |
| `JWT_SECRET` | dev value | Base64, ≥ 32 bytes; **required in `prod`** (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | comma-separated frontend origins |
| `UPLOAD_DIR` | `./uploads` | photo storage (random file names, outside the web root) |
| `AUTH_RATE_LIMIT` / `SUBMIT_RATE_LIMIT` / `PUBLIC_RATE_LIMIT` | 10 / 5 / 30 per minute | per IP |
| `AI_PROVIDER` | `mock` | `mock` or `spring-ai` |
| `SPRING_AI_CHAT` / `SPRING_AI_EMBEDDING` | `none` | set to `openai` with `AI_PROVIDER=spring-ai` |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_CHAT_MODEL`, `AI_EMBED_MODEL`, `AI_CHAT_PATH`, `AI_EMBED_PATH` | Gemini values | see AI_DESIGN.md |
| `AI_TIMEOUT_SECONDS` | 20 | hard timeout per AI call |
| `AI_INPUT_COST_PER_1K` / `AI_OUTPUT_COST_PER_1K` | 0 | cost estimate on the AI usage page |
| `DUP_MIN_SEMANTIC` | 0.40 | duplicate threshold (≈0.75 for real embedding models) |

## Free hosting options (portfolio demo)

| Part | Free option | Note |
|---|---|---|
| Database | Neon or Supabase PostgreSQL (both offer pgvector and PostGIS) | check current free-tier limits |
| Backend | Render / Railway / Fly.io with the backend Dockerfile | free instances may sleep when idle |
| Frontend | Netlify / Vercel / Cloudflare Pages (`npm run build`, publish `dist`) | set `VITE_API_BASE` to the backend URL and add the site to `CORS_ALLOWED_ORIGINS` |
| AI | mock (free) or Gemini free tier | keep real citizen data off free tiers |

## Production checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`, strong `JWT_SECRET` and `DB_PASSWORD` from a secret store; `.env` never committed
- [ ] HTTPS in front (Nginx/Caddy/cloud load balancer); `forward-headers-strategy` is already set in `prod`
- [ ] Remove the `127.0.0.1` port mappings for db/backend from compose
- [ ] Daily database backups (`pg_dump`) and a backup of the uploads volume (or move photos to object storage)
- [ ] Rate limiter: single instance only; use Redis/Bucket4j before running several backend instances
- [ ] Real ward boundaries imported via `POST /api/wards` (GeoJSON)
- [ ] Monitor `/actuator/health`, logs, and the AI usage page for failures/costs

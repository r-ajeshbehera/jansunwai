# API reference

Base URL: `http://localhost:8080` (or `/api` behind Nginx on port 3000). Interactive docs: **Swagger UI** at `/swagger-ui.html`; raw spec: [openapi.json](openapi.json).

Authentication: `POST /api/auth/login` returns `accessToken`; send `Authorization: Bearer <token>`. Tokens expire after 60 minutes; `POST /api/auth/logout` revokes the token (stored in `revoked_tokens`).

Errors always use one shape:

```json
{ "timestamp": "…", "status": 422, "code": "EVIDENCE_REQUIRED", "message": "Upload at least one photo of the completed work first", "path": "/api/complaints/7/status", "fieldErrors": {} }
```

| Status | Meaning |
|---|---|
| 400 | validation failed (`fieldErrors`) |
| 401 | no / invalid / revoked token |
| 403 | role not allowed |
| 404 | not found **or not visible to you** (no existence leak) |
| 409 | invalid state transition, concurrent update, duplicate |
| 413 / 415 | file too large / not a real image |
| 422 | business rule (e.g. `REASON_REQUIRED`, `WRONG_DEPARTMENT`, `EMAIL_TAKEN`) |
| 429 | rate limit (login 10/min, submit 5/min, public 30/min per IP) |

## AI: classification, duplicates, usage

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/ai/analysis/{complaintId}` | DEPARTMENT_OFFICER, ADMIN | analysis |
| POST | `/api/ai/classify/{complaintId}` | DEPARTMENT_OFFICER, ADMIN | Re-run AI classification (suggestion only; the officer still decides) |
| POST | `/api/ai/duplicates/candidates/{candidateId}/approve` | DEPARTMENT_OFFICER, ADMIN | Human approval: link the newer complaint to the older one (each keeps its tracking ID) |
| POST | `/api/ai/duplicates/candidates/{candidateId}/reject` | DEPARTMENT_OFFICER, ADMIN | reject |
| GET | `/api/ai/duplicates/pending` | DEPARTMENT_OFFICER, ADMIN | pending |
| POST | `/api/ai/duplicates/{complaintId}/detect` | DEPARTMENT_OFFICER, ADMIN | detect |
| GET | `/api/ai/provider` | DEPARTMENT_OFFICER, ADMIN | provider |
| GET | `/api/ai/usage` | ADMIN | AI calls, failures, tokens, estimated cost and latency per provider/model |

## Administration

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/audit-logs` | ADMIN | auditLogs |
| GET | `/api/users` | ADMIN | users |
| POST | `/api/users` | ADMIN | create |
| PATCH | `/api/users/{id}` | ADMIN | update |

## Authentication & profile

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Log in and receive a JWT (rate-limited) |
| POST | `/api/auth/logout` | authenticated | logout |
| GET | `/api/auth/me` | authenticated | me |
| POST | `/api/auth/register` | public | Citizen self-registration (always creates a CITIZEN account) |
| PUT | `/api/users/me` | authenticated | updateProfile |
| PATCH | `/api/users/me/password` | authenticated | changePassword |

## Complaints

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/complaints` | authenticated | List complaints visible to the caller (citizen: own, field officer: assigned, officer: department) |
| POST | `/api/complaints` | CITIZEN | Submit a complaint (en/hi/or). AI analysis runs in the background after saving. |
| GET | `/api/complaints/track/{trackingId}` | public | Public tracking by tracking ID (no login, no personal data) |
| GET | `/api/complaints/{id}` | authenticated | get |
| POST | `/api/complaints/{id}/assign` | DEPARTMENT_OFFICER, ADMIN | assign |
| POST | `/api/complaints/{id}/attachments` | CITIZEN, FIELD_OFFICER | Upload a JPEG/PNG/WEBP photo (citizen: evidence, assigned field officer: resolution proof) |
| GET | `/api/complaints/{id}/attachments/{attachmentId}` | authenticated | download |
| POST | `/api/complaints/{id}/remarks` | authenticated | addRemark |
| POST | `/api/complaints/{id}/review` | DEPARTMENT_OFFICER, ADMIN | Approve (with human-confirmed classification), reroute or reject an UNDER_REVIEW complaint |
| PATCH | `/api/complaints/{id}/status` | authenticated | Workflow step: start work, submit resolution, approve resolution, send back, close, reopen |

## Dashboards, maps & ward intelligence

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/dashboard/department/{departmentId}` | DEPARTMENT_OFFICER, ADMIN | department |
| GET | `/api/dashboard/summary` | DEPARTMENT_OFFICER, ADMIN | summary |
| GET | `/api/dashboard/ward/{wardId}` | DEPARTMENT_OFFICER, ADMIN | ward |
| GET | `/api/dashboard/weekly-summary` | DEPARTMENT_OFFICER, ADMIN | Ward summaries for the week containing 'date' (facts from SQL + labelled AI narrative) |
| POST | `/api/dashboard/weekly-summary/generate` | ADMIN | generate |
| GET | `/api/geo/heatmap` | DEPARTMENT_OFFICER, ADMIN | heatmap |
| GET | `/api/geo/hotspots` | DEPARTMENT_OFFICER, ADMIN | hotspots |
| GET | `/api/geo/nearby-summary` | authenticated | Counts of open complaints near a point (shown before submitting, to reduce duplicates) |

## Departments & wards

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/departments` | public | departments |
| POST | `/api/departments` | ADMIN | createDepartment |
| PUT | `/api/departments/{id}` | ADMIN | updateDepartment |
| GET | `/api/departments/{id}/complaints` | DEPARTMENT_OFFICER, ADMIN | departmentComplaints |
| GET | `/api/departments/{id}/field-officers` | DEPARTMENT_OFFICER, ADMIN | fieldOfficers |
| GET | `/api/wards` | public | wards |
| POST | `/api/wards` | ADMIN | createWard |
| GET | `/api/wards/shapes` | public | wardShapes |

## Notifications

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/notifications` | authenticated | mine |
| GET | `/api/notifications/unread-count` | authenticated | unread |
| POST | `/api/notifications/{id}/read` | authenticated | markRead |

## Public

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/public/track/{trackingId}` | public | track |

## SLA & escalations

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/escalations` | DEPARTMENT_OFFICER, ADMIN | escalations |
| GET | `/api/sla/configurations` | DEPARTMENT_OFFICER, ADMIN | configurations |
| POST | `/api/sla/configurations` | ADMIN | save |
| GET | `/api/sla/overdue` | DEPARTMENT_OFFICER, ADMIN | overdue |

_52 operations, generated from the running application's OpenAPI spec._

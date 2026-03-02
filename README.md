# Web-based Loan Approval Decision Support System

Week 1 deliverable implemented:
- Spring Boot backend scaffold with health endpoints.
- MySQL schema migration with Flyway.
- Docker Compose stack for `mysql + backend + frontend`.

Week 2 deliverable implemented:
- JWT authentication (`register`, `login`, `me`).
- Role-based access control for `CUSTOMER`, `STAFF`, and `ADMIN`.
- Customer profile APIs (`GET/PUT /api/customer/profile`).
- Frontend integration with backend auth APIs (no longer mock token).

Week 3 deliverable implemented:
- Customer loan request APIs (`create`, `list mine`, `detail mine`).
- Ownership validation: customer only sees own loan requests.
- Frontend customer pages integrated with real APIs:
  - Create loan request page
  - My loan requests list page
  - Loan request detail page

Week 4 deliverable implemented:
- Decision Engine with weighted credit scoring.
- Risk ranking (`A`, `B`, `C`, `D`).
- DSS recommendation rules:
  - `A + low DTI -> APPROVE_RECOMMENDED`
  - `D -> REJECT_RECOMMENDED`
  - `B/C + borderline -> ESCALATE_RECOMMENDED`
- Customer segmentation (`Risk-Value`):
  - `LOW_RISK_HIGH_VALUE`
  - `LOW_RISK_LOW_VALUE`
  - `HIGH_RISK_HIGH_VALUE`
  - `HIGH_RISK_LOW_VALUE`
- DSS snapshot persistence into `dss_results` when a loan request is created.

## Project Structure

```text
.
|- backend
|  |- src/main/java/com/loanapproval/dss
|  |- src/main/resources/db/migration
|  |- Dockerfile
|  `- pom.xml
|- frontend
|  |- src
|  `- Dockerfile
|- docker-compose.yml
`- .env.example
```

## Tech Stack

- Frontend: React + Vite + MUI
- Backend: Java 17 + Spring Boot
- Database: MySQL 8
- Migration: Flyway
- Deployment (local): Docker Compose

## Quick Start (Docker)

1. Copy env file:

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

2. Run stack:

```bash
docker compose up --build
```

3. Verify services:
- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`
- Actuator health: `http://localhost:8080/actuator/health`
- MySQL: `localhost:${MYSQL_PORT}` (default in `.env.example` is `3306`)

4. Default admin account (auto-bootstrap on first start):
- Email: `${APP_BOOTSTRAP_ADMIN_EMAIL}` (default `admin@loan.local`)
- Password: `${APP_BOOTSTRAP_ADMIN_PASSWORD}` (default `Admin123!`)
- Please change these values in `.env` for safer local use.

## Backend Notes

- Main app entry:
  - `backend/src/main/java/com/loanapproval/dss/LoanDssApplication.java`
- Health API:
  - `GET /api/health`
- Auth APIs:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `GET /api/auth/me`
- Admin user management APIs (ADMIN role):
  - `GET /api/admin/users?role=CUSTOMER|STAFF` (role is optional)
  - `DELETE /api/admin/users/{id}` (deletes CUSTOMER/STAFF and related data)
- Customer profile APIs (CUSTOMER role):
  - `GET /api/customer/profile`
  - `PUT /api/customer/profile`
- Customer loan APIs (CUSTOMER role):
  - `POST /api/customer/loans`
  - `GET /api/customer/loans`
  - `GET /api/customer/loans/{id}`
- DB migration:
  - `backend/src/main/resources/db/migration/V1__init_schema.sql`
  - `backend/src/main/resources/db/migration/V2__add_admin_role.sql`

Initial schema includes:
- `users`
- `customer_profiles`
- `loan_requests`
- `dss_results`
- `decision_audits`

## DSS Flow (Week 4)

When customer creates a loan request:
1. Backend stores loan request.
2. Decision engine computes:
   - `credit_score`
   - `risk_rank`
   - `customer_segment`
   - `recommendation`
   - `explanation`
3. Backend stores DSS output in `dss_results`.
4. If recommendation is `ESCALATE_RECOMMENDED`, request status is set to `WAITING_SUPERVISOR`.

## Quick API Smoke Test

Example register request:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"customer1@example.com","password":"Password123!","role":"CUSTOMER"}'
```

Then login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer1@example.com","password":"Password123!"}'
```

Use returned `accessToken` as `Bearer` token for profile/loan endpoints.

Create a loan request:

```bash
curl -X POST http://localhost:8080/api/customer/loans \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"amount":15000,"termMonths":24,"purpose":"PERSONAL"}'
```

List my loan requests:

```bash
curl -X GET http://localhost:8080/api/customer/loans \
  -H "Authorization: Bearer <accessToken>"
```

## Development Roadmap Alignment

This repository is currently at:
- Week 1 complete (infrastructure + schema + Docker stack).
- Week 2 complete (JWT auth + RBAC + customer profile APIs + frontend auth integration).
- Week 3 complete (customer loan create/list/detail APIs + frontend integration).
- Week 4 complete (credit scoring + risk ranking + recommendation + segmentation + dss_results persistence).
- Next: Week 5 (staff review APIs/UI and final decision workflow).

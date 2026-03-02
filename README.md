# Web-based Loan Approval Decision Support System

Week 1 deliverable implemented:
- Spring Boot backend scaffold with health endpoints.
- MySQL schema migration with Flyway.
- Docker Compose stack for `mysql + backend + frontend`.

Week 2 deliverable implemented:
- JWT authentication (`register`, `login`, `me`).
- Role-based access control for `CUSTOMER` and `STAFF`.
- Customer profile APIs (`GET/PUT /api/customer/profile`).
- Frontend integration with backend auth APIs (no longer mock token).

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

## Backend Notes

- Main app entry:
  - `backend/src/main/java/com/loanapproval/dss/LoanDssApplication.java`
- Health API:
  - `GET /api/health`
- Auth APIs:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `GET /api/auth/me`
- Customer profile APIs (CUSTOMER role):
  - `GET /api/customer/profile`
  - `PUT /api/customer/profile`
- DB migration:
  - `backend/src/main/resources/db/migration/V1__init_schema.sql`

Initial schema includes:
- `users`
- `customer_profiles`
- `loan_requests`
- `dss_results`
- `decision_audits`

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

Use returned `accessToken` as `Bearer` token for profile endpoints.

## Development Roadmap Alignment

This repository is currently at:
- Week 1 complete (infrastructure + schema + Docker stack).
- Week 2 complete (JWT auth + RBAC + customer profile APIs + frontend auth integration).
- Next: Week 3 (loan request create/list/detail APIs and frontend wiring to real data).

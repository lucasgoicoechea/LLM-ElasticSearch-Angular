# Equipment Catalog Local Workflow

Run the Kotlin backend, Python gateway, and Angular frontend locally to use the
Equipment catalog CRUD page. The browser talks to the gateway at `/api/equipment`;
the gateway forwards to the backend internal boundary.

## Quick path

Open three PowerShell terminals from the repository root:

```powershell
# Terminal 1: backend with fast in-memory persistence
cd backend
.\gradlew.bat bootRun

# Terminal 2: gateway
cd gateway
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
$env:BACKEND_BASE_URL="http://localhost:8080"
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000

# Terminal 3: Angular frontend
cd frontend
npm install
npm start
```

Open `http://localhost:4200`. The Angular dev proxy sends `/api` requests to
`http://localhost:8000`, so the UI never needs a direct backend URL.

## Public API contract

The reviewed public contract is versioned at
`openapi/v1/equipment-api.yaml`. While the gateway is running, clients can
retrieve the same contract from:

```text
GET http://localhost:8000/openapi/v1/equipment-api.yaml
```

The gateway test suite rejects drift between the authored contract and the
runtime Equipment routes. It also checks the public DTOs, success statuses, and
normalized error envelopes.

## Prerequisites

- JDK 17 or newer
- Python 3.11 or newer
- Node.js and npm compatible with the frontend `packageManager`
- Docker Compose for local Elasticsearch and Testcontainers verification

## Run with Elasticsearch persistence

Start the pinned local Elasticsearch service before selecting real persistence:

```powershell
docker compose up -d elasticsearch
$env:EQUIPMENT_REPOSITORY="elasticsearch"
cd backend
.\gradlew.bat bootRun
```

Elasticsearch mode uses the real adapter and requires the pinned local service
to be reachable. Stop the local service with `docker compose down`.

## Test and build

Run the full local regression path before review:

```powershell
# Angular component/service tests and production build
cd frontend
npm test -- --watch=false
npm run build

# Gateway forwarding and OpenAPI drift tests
cd ..\gateway
.\.venv\Scripts\python -m pip install -e ".[dev]"
.\.venv\Scripts\python -m pytest

# Backend unit and Testcontainers integration tests
cd ..\backend
.\gradlew.bat test --rerun-tasks --no-daemon --console=plain

# Compose and whitespace checks from repo root
cd ..
docker compose config
git diff --check
```

To validate only the public contract:

```powershell
cd gateway
.\.venv\Scripts\python -m pytest -q tests\test_openapi_contract.py
```

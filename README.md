# Equipment Catalog Bootstrap

Run the Kotlin backend and Python gateway locally to use the Equipment catalog
CRUD API. The gateway exposes `/api/equipment` and forwards requests to the
backend internal boundary.

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

## Run the backend

```powershell
cd backend
.\gradlew.bat bootRun
```

## Run the gateway

Open a second PowerShell terminal:

```powershell
cd gateway
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
$env:BACKEND_BASE_URL="http://localhost:8080"
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000
```

`BACKEND_BASE_URL` defaults to `http://localhost:8080` when it is not set.

## Run tests

```powershell
cd backend
.\gradlew.bat test

cd ..\gateway
.\.venv\Scripts\python -m pip install -e ".[dev]"
.\.venv\Scripts\python -m pytest
```

To validate only the public contract:

```powershell
cd gateway
.\.venv\Scripts\python -m pytest -q tests\test_openapi_contract.py
```

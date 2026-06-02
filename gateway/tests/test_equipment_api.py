from collections.abc import Callable
import json
from typing import Any

import httpx
import pytest
from fastapi.testclient import TestClient

from app.backend_client import BackendClient
from app.main import app, get_backend_client


EQUIPMENT_ID = "ba5d56ce-8209-4f22-bbab-782ad8193707"
EQUIPMENT = {
    "id": EQUIPMENT_ID,
    "code": "PUMP-01",
    "name": "Main Pump",
    "status": "ACTIVE",
}
WRITE_PAYLOAD = {
    "code": "PUMP-01",
    "name": "Main Pump",
    "status": "ACTIVE",
}


def client_for(
    handler: Callable[[httpx.Request], httpx.Response],
) -> TestClient:
    backend = BackendClient(
        base_url="http://backend.test",
        transport=httpx.MockTransport(handler),
    )
    app.dependency_overrides[get_backend_client] = lambda: backend
    return TestClient(app)


@pytest.fixture(autouse=True)
def reset_dependency_overrides() -> None:
    app.dependency_overrides.clear()
    yield
    app.dependency_overrides.clear()


@pytest.mark.parametrize(
    ("method", "public_path", "internal_path", "payload", "backend_status", "backend_body"),
    [
        ("post", "/api/equipment", "/internal/equipment", WRITE_PAYLOAD, 201, EQUIPMENT),
        ("get", "/api/equipment", "/internal/equipment", None, 200, [EQUIPMENT]),
        ("get", f"/api/equipment/{EQUIPMENT_ID}", f"/internal/equipment/{EQUIPMENT_ID}", None, 200, EQUIPMENT),
        ("put", f"/api/equipment/{EQUIPMENT_ID}", f"/internal/equipment/{EQUIPMENT_ID}", WRITE_PAYLOAD, 200, EQUIPMENT),
        ("delete", f"/api/equipment/{EQUIPMENT_ID}", f"/internal/equipment/{EQUIPMENT_ID}", None, 204, None),
    ],
)
def test_forwards_public_equipment_endpoints(
    method: str,
    public_path: str,
    internal_path: str,
    payload: dict[str, str] | None,
    backend_status: int,
    backend_body: Any,
) -> None:
    received: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        received.append(request)
        return httpx.Response(backend_status, json=backend_body) if backend_body is not None else httpx.Response(backend_status)

    with client_for(handler) as client:
        response = client.request(method, public_path, json=payload)

    assert response.status_code == backend_status
    assert response.json() == backend_body if backend_body is not None else response.content == b""
    assert len(received) == 1
    assert received[0].method == method.upper()
    assert received[0].url.path == internal_path
    assert json.loads(received[0].content) == payload if payload is not None else received[0].content == b""


@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("post", "/api/equipment"),
        ("put", f"/api/equipment/{EQUIPMENT_ID}"),
    ],
)
@pytest.mark.parametrize(
    ("payload", "field"),
    [
        ({"name": "Main Pump", "status": "ACTIVE"}, "code"),
        ({**WRITE_PAYLOAD, "unexpected": "value"}, "unexpected"),
        ({**WRITE_PAYLOAD, "code": "   "}, "code"),
        ({**WRITE_PAYLOAD, "name": "   "}, "name"),
        ({**WRITE_PAYLOAD, "code": "C" * 65}, "code"),
        ({**WRITE_PAYLOAD, "name": "N" * 121}, "name"),
        ({**WRITE_PAYLOAD, "status": "RETIRED"}, "status"),
    ],
)
def test_rejects_invalid_public_payload_before_forwarding(
    method: str,
    path: str,
    payload: dict[str, str],
    field: str,
) -> None:
    received: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        received.append(request)
        return httpx.Response(201, json=EQUIPMENT)

    with client_for(handler) as client:
        response = client.request(method, path, json=payload)

    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"
    assert response.json()["message"] == "Request validation failed"
    assert field in {error["field"] for error in response.json()["errors"]}
    assert received == []


@pytest.mark.parametrize(
    ("method", "path", "payload", "status_code", "error"),
    [
        ("post", "/api/equipment", WRITE_PAYLOAD, 400, {"code": "VALIDATION_ERROR", "message": "Request validation failed", "errors": [{"field": "name", "message": "must not be blank"}]}),
        ("get", f"/api/equipment/{EQUIPMENT_ID}", None, 404, {"code": "EQUIPMENT_NOT_FOUND", "message": f"Equipment {EQUIPMENT_ID} was not found"}),
        ("put", f"/api/equipment/{EQUIPMENT_ID}", WRITE_PAYLOAD, 409, {"code": "EQUIPMENT_CODE_CONFLICT", "message": "Equipment code PUMP-01 already exists"}),
    ],
)
def test_preserves_backend_error_semantics(
    method: str,
    path: str,
    payload: dict[str, str] | None,
    status_code: int,
    error: dict[str, Any],
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code, json=error)

    with client_for(handler) as client:
        response = client.request(method, path, json=payload)

    assert response.status_code == status_code
    assert response.json() == error


def test_maps_backend_transport_failure_to_bad_gateway() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("backend refused connection", request=request)

    with client_for(handler) as client:
        response = client.get("/api/equipment")

    assert response.status_code == 502
    assert response.json() == {
        "code": "BACKEND_UNAVAILABLE",
        "message": "Equipment backend is unavailable",
    }

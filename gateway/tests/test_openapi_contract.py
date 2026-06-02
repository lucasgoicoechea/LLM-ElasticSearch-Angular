from copy import deepcopy
from typing import Any

import yaml
from fastapi.routing import APIRoute
from fastapi.testclient import TestClient

from app.main import app


PUBLIC_EQUIPMENT_OPERATIONS = {
    "/api/equipment": {"get": "200", "post": "201"},
    "/api/equipment/{equipment_id}": {
        "delete": "204",
        "get": "200",
        "put": "200",
    },
}
ERROR_RESPONSES = {
    "400": "#/components/schemas/ApiError",
    "404": "#/components/schemas/ApiError",
    "409": "#/components/schemas/ApiError",
    "502": "#/components/schemas/ApiError",
}


def public_equipment_routes() -> dict[str, set[str]]:
    routes: dict[str, set[str]] = {}
    for route in app.routes:
        if isinstance(route, APIRoute) and route.path.startswith("/api/equipment"):
            routes.setdefault(route.path, set()).update(
                method.lower()
                for method in route.methods
                if method not in {"HEAD", "OPTIONS"}
            )
    return routes


def documented_equipment_routes(contract: dict[str, Any]) -> dict[str, set[str]]:
    return {
        path: set(operations) - {"parameters"}
        for path, operations in contract["paths"].items()
        if path.startswith("/api/equipment")
    }


def assert_contract_matches_runtime(contract: dict[str, Any]) -> None:
    assert documented_equipment_routes(contract) == public_equipment_routes()


def test_serves_versioned_authored_openapi_yaml() -> None:
    with TestClient(app) as client:
        response = client.get("/openapi/v1/equipment-api.yaml")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/yaml")
    assert yaml.safe_load(response.text) == app.openapi()


def test_authored_contract_describes_runtime_routes_and_rejects_route_drift() -> None:
    contract = app.openapi()

    assert_contract_matches_runtime(contract)

    drifted_contract = deepcopy(contract)
    del drifted_contract["paths"]["/api/equipment/{equipment_id}"]["delete"]

    try:
        assert_contract_matches_runtime(drifted_contract)
    except AssertionError:
        pass
    else:
        raise AssertionError("route drift must fail contract validation")


def test_authored_contract_describes_dtos_statuses_and_normalized_errors() -> None:
    contract = app.openapi()
    schemas = contract["components"]["schemas"]

    assert schemas["Equipment"]["required"] == ["id", "code", "name", "status"]
    assert schemas["Equipment"]["properties"]["id"]["format"] == "uuid"
    assert schemas["EquipmentStatus"]["enum"] == [
        "ACTIVE",
        "INACTIVE",
    ]
    assert schemas["EquipmentWriteRequest"]["additionalProperties"] is False
    assert schemas["EquipmentWriteRequest"]["required"] == ["code", "name", "status"]
    assert schemas["EquipmentWriteRequest"]["properties"]["code"]["maxLength"] == 64
    assert schemas["EquipmentWriteRequest"]["properties"]["name"]["maxLength"] == 120
    assert schemas["ApiError"]["required"] == ["code", "message"]
    assert schemas["ApiFieldError"]["required"] == ["field", "message"]

    for path, operations in PUBLIC_EQUIPMENT_OPERATIONS.items():
        for operation, success_status in operations.items():
            responses = contract["paths"][path][operation]["responses"]
            assert success_status in responses
            for error_status, schema_ref in ERROR_RESPONSES.items():
                if error_status in responses:
                    response = responses[error_status]
                    if "$ref" in response:
                        response = contract["components"]["responses"][
                            response["$ref"].rsplit("/", maxsplit=1)[-1]
                        ]
                    assert (
                        response["content"]["application/json"]["schema"]["$ref"]
                        == schema_ref
                    )

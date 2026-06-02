import os
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml
from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, Response

from app.backend_client import BackendClient, BackendUnavailableError
from app.schemas import ApiError, ApiFieldError, EquipmentWriteRequest


DEFAULT_BACKEND_BASE_URL = "http://localhost:8080"
OPENAPI_CONTRACT_PATH = (
    Path(__file__).resolve().parents[2] / "openapi" / "v1" / "equipment-api.yaml"
)

app = FastAPI(title="Equipment Catalog Gateway")


@lru_cache
def load_openapi_contract() -> dict[str, Any]:
    with OPENAPI_CONTRACT_PATH.open(encoding="utf-8") as contract_file:
        return yaml.safe_load(contract_file)


app.openapi = load_openapi_contract


@lru_cache
def get_backend_client() -> BackendClient:
    return BackendClient(
        base_url=os.getenv("BACKEND_BASE_URL", DEFAULT_BACKEND_BASE_URL),
    )


@app.get("/openapi/v1/equipment-api.yaml", include_in_schema=False)
async def get_openapi_contract() -> FileResponse:
    return FileResponse(OPENAPI_CONTRACT_PATH, media_type="application/yaml")


@app.exception_handler(RequestValidationError)
async def handle_request_validation(
    _request: Request,
    exception: RequestValidationError,
) -> JSONResponse:
    errors = [
        ApiFieldError(
            field=str(error["loc"][-1]),
            message=error["msg"],
        )
        for error in exception.errors()
    ]
    return JSONResponse(
        status_code=400,
        content=ApiError(
            code="VALIDATION_ERROR",
            message="Request validation failed",
            errors=errors,
        ).model_dump(exclude_none=True),
    )


async def forward(
    backend: BackendClient,
    method: str,
    path: str,
    *,
    payload: dict[str, str] | None = None,
) -> Response:
    try:
        response = await backend.request(method, path, json=payload)
    except BackendUnavailableError:
        return JSONResponse(
            status_code=502,
            content=ApiError(
                code="BACKEND_UNAVAILABLE",
                message="Equipment backend is unavailable",
            ).model_dump(exclude_none=True),
        )

    if response.status_code == 204:
        return Response(status_code=204)

    return JSONResponse(
        status_code=response.status_code,
        content=response.json(),
    )


@app.post("/api/equipment", status_code=201)
async def create_equipment(
    payload: EquipmentWriteRequest,
    backend: BackendClient = Depends(get_backend_client),
) -> Response:
    return await forward(
        backend,
        "POST",
        "/internal/equipment",
        payload=payload.model_dump(),
    )


@app.get("/api/equipment")
async def list_equipment(
    backend: BackendClient = Depends(get_backend_client),
) -> Response:
    return await forward(backend, "GET", "/internal/equipment")


@app.get("/api/equipment/{equipment_id}")
async def get_equipment(
    equipment_id: str,
    backend: BackendClient = Depends(get_backend_client),
) -> Response:
    return await forward(backend, "GET", f"/internal/equipment/{equipment_id}")


@app.put("/api/equipment/{equipment_id}")
async def update_equipment(
    equipment_id: str,
    payload: EquipmentWriteRequest,
    backend: BackendClient = Depends(get_backend_client),
) -> Response:
    return await forward(
        backend,
        "PUT",
        f"/internal/equipment/{equipment_id}",
        payload=payload.model_dump(),
    )


@app.delete("/api/equipment/{equipment_id}", status_code=204)
async def delete_equipment(
    equipment_id: str,
    backend: BackendClient = Depends(get_backend_client),
) -> Response:
    return await forward(backend, "DELETE", f"/internal/equipment/{equipment_id}")

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, field_validator


class EquipmentWriteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    name: str
    status: Literal["ACTIVE", "INACTIVE"]

    @field_validator("code")
    @classmethod
    def validate_code(cls, value: str) -> str:
        return validate_text(value=value, field="code", max_length=64)

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        return validate_text(value=value, field="name", max_length=120)


class EquipmentResponse(BaseModel):
    id: UUID
    code: str
    name: str
    status: Literal["ACTIVE", "INACTIVE"]


class ApiFieldError(BaseModel):
    field: str
    message: str


class ApiError(BaseModel):
    code: str
    message: str
    errors: list[ApiFieldError] | None = None


def validate_text(*, value: str, field: str, max_length: int) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field} must not be blank")
    if len(normalized) > max_length:
        raise ValueError(f"{field} must contain at most {max_length} characters")
    return value

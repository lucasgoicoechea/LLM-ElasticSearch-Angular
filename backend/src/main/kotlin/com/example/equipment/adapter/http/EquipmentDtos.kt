package com.example.equipment.adapter.http

import com.example.equipment.domain.Equipment
import com.example.equipment.domain.EquipmentStatus
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class EquipmentWriteRequest(
    val code: String,
    val name: String,
    val status: EquipmentStatus,
) {
    fun validate() {
        val errors = buildList {
            addTextError(field = "code", value = code, maxLength = 64)
            addTextError(field = "name", value = name, maxLength = 120)
        }
        if (errors.isNotEmpty()) {
            throw RequestValidationException(errors)
        }
    }
}

data class EquipmentResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val status: EquipmentStatus,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(
    val code: String,
    val message: String,
    val errors: List<ApiFieldError>? = null,
)

data class ApiFieldError(
    val field: String,
    val message: String,
)

class RequestValidationException(
    val errors: List<ApiFieldError>,
) : RuntimeException("Request validation failed")

fun Equipment.toResponse(): EquipmentResponse =
    EquipmentResponse(
        id = id,
        code = code,
        name = name,
        status = status,
    )

private fun MutableList<ApiFieldError>.addTextError(field: String, value: String, maxLength: Int) {
    val normalized = value.trim()
    when {
        normalized.isEmpty() -> add(ApiFieldError(field = field, message = "must not be blank"))
        normalized.length > maxLength ->
            add(ApiFieldError(field = field, message = "size must be between 0 and $maxLength"))
    }
}

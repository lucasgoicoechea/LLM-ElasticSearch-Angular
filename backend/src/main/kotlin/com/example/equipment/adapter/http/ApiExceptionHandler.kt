package com.example.equipment.adapter.http

import com.example.equipment.application.EquipmentCodeConflictException
import com.example.equipment.application.EquipmentNotFoundException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(EquipmentNotFoundException::class)
    fun handleNotFound(exception: EquipmentNotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(
                code = "EQUIPMENT_NOT_FOUND",
                message = exception.message.orEmpty(),
            ),
        )

    @ExceptionHandler(EquipmentCodeConflictException::class)
    fun handleConflict(exception: EquipmentCodeConflictException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(
                code = "EQUIPMENT_CODE_CONFLICT",
                message = exception.message.orEmpty(),
            ),
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidDomainField(exception: IllegalArgumentException): ResponseEntity<ApiError> {
        val message = exception.message.orEmpty()
        val field = when {
            message.contains("code", ignoreCase = true) -> "code"
            message.contains("name", ignoreCase = true) -> "name"
            else -> "request"
        }

        return validationError(ApiFieldError(field = field, message = message))
    }

    @ExceptionHandler(RequestValidationException::class)
    fun handleInvalidRequest(exception: RequestValidationException): ResponseEntity<ApiError> =
        validationError(exception.errors)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> {
        val message =
            if (exception.requiredType == UUID::class.java) {
                "must be a valid UUID"
            } else {
                "has an invalid value"
            }

        return validationError(ApiFieldError(field = exception.name, message = message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(exception: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        val fieldError =
            when (val cause = exception.cause) {
                is UnrecognizedPropertyException ->
                    ApiFieldError(field = cause.propertyName, message = "Unknown field")
                is InvalidFormatException ->
                    ApiFieldError(field = cause.normalizedField(), message = "has an invalid value")
                is MismatchedInputException ->
                    ApiFieldError(field = cause.normalizedField(), message = "is required")
                else ->
                    ApiFieldError(field = "request", message = "Malformed JSON request")
            }

        return validationError(fieldError)
    }

    private fun validationError(vararg errors: ApiFieldError): ResponseEntity<ApiError> =
        validationError(errors.toList())

    private fun validationError(errors: List<ApiFieldError>): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                errors = errors,
            ),
        )

    private fun MismatchedInputException.normalizedField(): String =
        path.lastOrNull()?.fieldName?.takeIf { it.isNotBlank() } ?: "request"
}

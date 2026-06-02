package com.example.equipment.adapter.http

import com.example.equipment.application.EquipmentService
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/equipment")
class EquipmentController(
    private val service: EquipmentService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: EquipmentWriteRequest): EquipmentResponse {
        request.validate()
        return service.create(
            code = request.code,
            name = request.name,
            status = request.status,
        ).toResponse()
    }

    @GetMapping
    fun list(): List<EquipmentResponse> = service.list().map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): EquipmentResponse = service.get(id).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: EquipmentWriteRequest,
    ): EquipmentResponse {
        request.validate()
        return service.update(
            id = id,
            code = request.code,
            name = request.name,
            status = request.status,
        ).toResponse()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        service.delete(id)
    }
}

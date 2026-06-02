package com.example.equipment.application

import com.example.equipment.domain.Equipment
import com.example.equipment.domain.EquipmentStatus
import java.util.UUID

class EquipmentService(
    private val repository: EquipmentRepository,
) {
    fun create(code: String, name: String, status: EquipmentStatus): Equipment {
        return repository.create(
            code = code,
            name = name,
            status = status,
        )
    }

    fun get(id: UUID): Equipment =
        repository.findById(id) ?: throw EquipmentNotFoundException(id)

    fun list(): List<Equipment> = repository.findAll()

    fun update(id: UUID, code: String, name: String, status: EquipmentStatus): Equipment {
        return repository.update(
            id = id,
            code = code,
            name = name,
            status = status,
        ) ?: throw EquipmentNotFoundException(id)
    }

    fun delete(id: UUID) {
        if (!repository.deleteById(id)) {
            throw EquipmentNotFoundException(id)
        }
    }
}

class EquipmentNotFoundException(id: UUID) :
    RuntimeException("Equipment not found: $id")

class EquipmentCodeConflictException(code: String) :
    RuntimeException("Equipment code already exists: $code")

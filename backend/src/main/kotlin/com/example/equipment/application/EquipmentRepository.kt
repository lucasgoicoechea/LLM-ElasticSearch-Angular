package com.example.equipment.application

import com.example.equipment.domain.Equipment
import com.example.equipment.domain.EquipmentStatus
import java.util.UUID

interface EquipmentRepository {
    fun create(code: String, name: String, status: EquipmentStatus): Equipment

    fun findById(id: UUID): Equipment?

    fun findAll(): List<Equipment>

    fun update(id: UUID, code: String, name: String, status: EquipmentStatus): Equipment?

    fun deleteById(id: UUID): Boolean
}

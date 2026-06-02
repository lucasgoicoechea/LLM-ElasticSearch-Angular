package com.example.equipment.adapter.inmemory

import com.example.equipment.application.EquipmentRepository
import com.example.equipment.application.EquipmentCodeConflictException
import com.example.equipment.domain.Equipment
import com.example.equipment.domain.EquipmentStatus
import java.util.UUID

class InMemoryEquipmentRepository : EquipmentRepository {
    private val equipmentById = linkedMapOf<UUID, Equipment>()

    @Synchronized
    override fun create(code: String, name: String, status: EquipmentStatus): Equipment {
        val equipment = Equipment.create(
            id = UUID.randomUUID(),
            code = code,
            name = name,
            status = status,
        )
        requireAvailable(equipment.code)
        equipmentById[equipment.id] = equipment
        return equipment
    }

    @Synchronized
    override fun findById(id: UUID): Equipment? = equipmentById[id]

    @Synchronized
    override fun findAll(): List<Equipment> = equipmentById.values.toList()

    @Synchronized
    override fun update(id: UUID, code: String, name: String, status: EquipmentStatus): Equipment? {
        val current = equipmentById[id] ?: return null
        val updated = current.replace(code = code, name = name, status = status)
        requireAvailable(updated.code, excludingId = id)
        equipmentById[id] = updated
        return updated
    }

    @Synchronized
    override fun deleteById(id: UUID): Boolean = equipmentById.remove(id) != null

    private fun requireAvailable(code: String, excludingId: UUID? = null) {
        val conflict = equipmentById.values.any { equipment ->
            equipment.code == code && equipment.id != excludingId
        }
        if (conflict) {
            throw EquipmentCodeConflictException(code)
        }
    }
}

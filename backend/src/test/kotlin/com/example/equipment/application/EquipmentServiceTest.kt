package com.example.equipment.application

import com.example.equipment.adapter.inmemory.InMemoryEquipmentRepository
import com.example.equipment.domain.EquipmentStatus
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EquipmentServiceTest {
    private val service = EquipmentService(InMemoryEquipmentRepository())

    @Test
    fun `creates equipment with an allocated id and trimmed fields`() {
        val created = service.create(
            code = "  PUMP-01  ",
            name = "  Main Pump  ",
            status = EquipmentStatus.ACTIVE,
        )

        assertEquals("PUMP-01", created.code)
        assertEquals("Main Pump", created.name)
        assertEquals(EquipmentStatus.ACTIVE, created.status)
    }

    @Test
    fun `allocates a different id for each created equipment`() {
        val first = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)
        val second = service.create("VALVE-02", "Safety Valve", EquipmentStatus.INACTIVE)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `gets created equipment by id`() {
        val created = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        assertEquals(created, service.get(created.id))
    }

    @Test
    fun `lists every created equipment`() {
        val first = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)
        val second = service.create("VALVE-02", "Safety Valve", EquipmentStatus.INACTIVE)

        assertEquals(listOf(first, second), service.list())
    }

    @Test
    fun `lists an empty catalog when no equipment exists`() {
        assertEquals(emptyList<Any>(), service.list())
    }

    @Test
    fun `updates mutable fields while preserving id and trimming values`() {
        val created = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        val updated = service.update(
            id = created.id,
            code = "  PUMP-02  ",
            name = "  Backup Pump  ",
            status = EquipmentStatus.INACTIVE,
        )

        assertEquals(created.id, updated.id)
        assertEquals("PUMP-02", updated.code)
        assertEquals("Backup Pump", updated.name)
        assertEquals(EquipmentStatus.INACTIVE, updated.status)
    }

    @Test
    fun `deletes existing equipment`() {
        val created = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        service.delete(created.id)

        assertThrows(EquipmentNotFoundException::class.java) {
            service.get(created.id)
        }
    }

    @Test
    fun `rejects get update and delete for missing equipment`() {
        val missingId = UUID.randomUUID()

        assertThrows(EquipmentNotFoundException::class.java) {
            service.get(missingId)
        }
        assertThrows(EquipmentNotFoundException::class.java) {
            service.update(missingId, "PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)
        }
        assertThrows(EquipmentNotFoundException::class.java) {
            service.delete(missingId)
        }
    }

    @Test
    fun `rejects duplicate code on create after trimming`() {
        service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        assertThrows(EquipmentCodeConflictException::class.java) {
            service.create("  PUMP-01  ", "Backup Pump", EquipmentStatus.INACTIVE)
        }
    }

    @Test
    fun `rejects duplicate code on update while preserving the original equipment`() {
        val first = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)
        val second = service.create("VALVE-02", "Safety Valve", EquipmentStatus.INACTIVE)

        assertThrows(EquipmentCodeConflictException::class.java) {
            service.update(second.id, "  PUMP-01  ", "Replacement Valve", EquipmentStatus.ACTIVE)
        }

        assertEquals(second, service.get(second.id))
        assertEquals(first, service.get(first.id))
    }

    @Test
    fun `rejects blank and oversized fields on create`() {
        invalidFields().forEach { (code, name) ->
            assertThrows(IllegalArgumentException::class.java) {
                service.create(code, name, EquipmentStatus.ACTIVE)
            }
        }
    }

    @Test
    fun `rejects blank and oversized fields on update while preserving the original equipment`() {
        val created = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        invalidFields().forEach { (code, name) ->
            assertThrows(IllegalArgumentException::class.java) {
                service.update(created.id, code, name, EquipmentStatus.INACTIVE)
            }
        }

        assertEquals(created, service.get(created.id))
    }

    @Test
    fun `repository construction path trims and validates fields`() {
        val repository = InMemoryEquipmentRepository()

        val created = repository.create("  PUMP-01  ", "  Main Pump  ", EquipmentStatus.ACTIVE)

        assertEquals("PUMP-01", created.code)
        assertEquals("Main Pump", created.name)
        assertThrows(IllegalArgumentException::class.java) {
            repository.create("   ", "Backup Pump", EquipmentStatus.INACTIVE)
        }
    }

    @Test
    fun `repository replacement path trims and validates fields while preserving the original equipment`() {
        val repository = InMemoryEquipmentRepository()
        val created = repository.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)

        val updated = repository.update(
            id = created.id,
            code = "  PUMP-02  ",
            name = "  Backup Pump  ",
            status = EquipmentStatus.INACTIVE,
        )

        assertEquals("PUMP-02", updated?.code)
        assertEquals("Backup Pump", updated?.name)
        assertThrows(IllegalArgumentException::class.java) {
            repository.update(created.id, "PUMP-03", "x".repeat(121), EquipmentStatus.ACTIVE)
        }
        assertEquals(updated, repository.findById(created.id))
    }

    @Test
    fun `treats codes with different casing as distinct`() {
        val uppercase = service.create("PUMP-01", "Main Pump", EquipmentStatus.ACTIVE)
        val lowercase = service.create("pump-01", "Backup Pump", EquipmentStatus.INACTIVE)

        assertEquals("PUMP-01", uppercase.code)
        assertEquals("pump-01", lowercase.code)
        assertEquals(listOf(uppercase, lowercase), service.list())
    }

    private fun invalidFields(): List<Pair<String, String>> = listOf(
        "   " to "Main Pump",
        "PUMP-01" to "   ",
        "x".repeat(65) to "Main Pump",
        "PUMP-01" to "x".repeat(121),
    )
}

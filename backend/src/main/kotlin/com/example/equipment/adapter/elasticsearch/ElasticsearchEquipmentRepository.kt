package com.example.equipment.adapter.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.search.Hit
import org.elasticsearch.client.ResponseException
import com.example.equipment.application.EquipmentCodeConflictException
import com.example.equipment.application.EquipmentRepository
import com.example.equipment.domain.Equipment
import com.example.equipment.domain.EquipmentStatus
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

const val EQUIPMENT_INDEX = "equipment-v1"
const val EQUIPMENT_CODE_RESERVATIONS_INDEX = "equipment-code-reservations-v1"

class ElasticsearchEquipmentRepository(
    private val client: ElasticsearchClient,
    private val hooks: ElasticsearchEquipmentRepositoryHooks = ElasticsearchEquipmentRepositoryHooks(),
    private val idSupplier: () -> UUID = UUID::randomUUID,
) : EquipmentRepository {
    override fun create(code: String, name: String, status: EquipmentStatus): Equipment {
        ensureIndices()
        val equipment = Equipment.create(idSupplier(), code, name, status)
        reserve(equipment.code, equipment.id)
        try {
            client.create<EquipmentDocument> {
                it.index(EQUIPMENT_INDEX)
                    .id(equipment.id.toString())
                    .document(equipment.toDocument())
                    .refresh(Refresh.WaitFor)
            }
        } catch (exception: Exception) {
            deleteOwnedReservation(equipment.code, equipment.id)
            throw exception
        }
        return equipment
    }

    override fun findById(id: UUID): Equipment? {
        ensureIndices()
        return load(id)?.equipment
    }

    override fun findAll(): List<Equipment> {
        ensureIndices()
        return client.search({ it.index(EQUIPMENT_INDEX).size(MAX_LIST_SIZE) }, EquipmentDocument::class.java)
            .hits()
            .hits()
            .mapNotNull(Hit<EquipmentDocument>::source)
            .map(EquipmentDocument::toEquipment)
    }

    override fun update(id: UUID, code: String, name: String, status: EquipmentStatus): Equipment? {
        ensureIndices()
        val stored = load(id) ?: return null
        val updated = stored.equipment.replace(code, name, status)
        if (stored.equipment.code == updated.code) {
            indexWithOcc(updated, stored)
            return updated
        }

        reserve(updated.code, updated.id)
        try {
            indexWithOcc(updated, stored)
        } catch (exception: Exception) {
            deleteOwnedReservation(updated.code, updated.id)
            throw exception
        }
        deleteOwnedReservation(stored.equipment.code, stored.equipment.id)
        return updated
    }

    override fun deleteById(id: UUID): Boolean {
        ensureIndices()
        val stored = load(id) ?: return false
        hooks.beforeOccWrite()
        client.delete {
            it.index(EQUIPMENT_INDEX)
                .id(id.toString())
                .ifSeqNo(stored.seqNo)
                .ifPrimaryTerm(stored.primaryTerm)
                .refresh(Refresh.WaitFor)
        }
        deleteOwnedReservation(stored.equipment.code, stored.equipment.id)
        return true
    }

    fun resetIndices() {
        listOf(EQUIPMENT_INDEX, EQUIPMENT_CODE_RESERVATIONS_INDEX).forEach { index ->
            if (client.indices().exists { it.index(index) }.value()) {
                client.indices().delete { it.index(index) }
            }
        }
        ensureIndices()
    }

    private fun indexWithOcc(equipment: Equipment, stored: StoredEquipment) {
        hooks.beforeOccWrite()
        client.index<EquipmentDocument> {
            it.index(EQUIPMENT_INDEX)
                .id(equipment.id.toString())
                .document(equipment.toDocument())
                .ifSeqNo(stored.seqNo)
                .ifPrimaryTerm(stored.primaryTerm)
                .refresh(Refresh.WaitFor)
        }
    }

    private fun load(id: UUID): StoredEquipment? {
        val response = client.get({ it.index(EQUIPMENT_INDEX).id(id.toString()) }, EquipmentDocument::class.java)
        val source = response.source() ?: return null
        return StoredEquipment(
            source.toEquipment(),
            requireNotNull(response.seqNo()) { "Elasticsearch get response must include _seq_no" },
            requireNotNull(response.primaryTerm()) { "Elasticsearch get response must include _primary_term" },
        )
    }

    private fun reserve(code: String, equipmentId: UUID) {
        try {
            client.create<EquipmentCodeReservationDocument> {
                it.index(EQUIPMENT_CODE_RESERVATIONS_INDEX)
                    .id(reservationId(code))
                    .document(EquipmentCodeReservationDocument(code, equipmentId.toString()))
                    .refresh(Refresh.WaitFor)
            }
        } catch (exception: Exception) {
            if (exception.isConflict()) {
                throw EquipmentCodeConflictException(code)
            }
            throw exception
        }
    }

    private fun deleteOwnedReservation(code: String, equipmentId: UUID) {
        var lastFailure: Exception? = null
        repeat(CLEANUP_RETRIES) {
            try {
                val reservation = client.get(
                    { request -> request.index(EQUIPMENT_CODE_RESERVATIONS_INDEX).id(reservationId(code)) },
                    EquipmentCodeReservationDocument::class.java,
                )
                val source = reservation.source() ?: return
                if (source.equipmentId != equipmentId.toString()) return
                hooks.beforeCleanupDelete()
                client.delete {
                    it.index(EQUIPMENT_CODE_RESERVATIONS_INDEX)
                        .id(reservationId(code))
                        .ifSeqNo(reservation.seqNo())
                        .ifPrimaryTerm(reservation.primaryTerm())
                        .refresh(Refresh.WaitFor)
                }
                return
            } catch (exception: Exception) {
                lastFailure = exception
            }
        }
        throw ReservationCleanupException(code, equipmentId, requireNotNull(lastFailure))
    }

    private fun ensureIndices() {
        createIndexIfMissing(EQUIPMENT_INDEX) {
            it.properties("id") { property -> property.keyword { keyword -> keyword } }
                .properties("code") { property -> property.keyword { keyword -> keyword } }
                .properties("name") { property -> property.keyword { keyword -> keyword } }
                .properties("status") { property -> property.keyword { keyword -> keyword } }
        }
        createIndexIfMissing(EQUIPMENT_CODE_RESERVATIONS_INDEX) {
            it.properties("code") { property -> property.keyword { keyword -> keyword } }
                .properties("equipmentId") { property -> property.keyword { keyword -> keyword } }
        }
    }

    private fun createIndexIfMissing(
        index: String,
        mappings: (co.elastic.clients.elasticsearch._types.mapping.TypeMapping.Builder) ->
        co.elastic.clients.elasticsearch._types.mapping.TypeMapping.Builder,
    ) {
        if (!client.indices().exists { it.index(index) }.value()) {
            hooks.beforeCreateIndex()
            try {
                client.indices().create { it.index(index).mappings(mappings) }
            } catch (exception: Exception) {
                if (!client.indices().exists { it.index(index) }.value()) throw exception
            }
        }
    }

    private fun reservationId(code: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(code.toByteArray(StandardCharsets.UTF_8))

    private fun Throwable.isConflict(): Boolean =
        generateSequence(this as Throwable?) { it.cause }.any { cause ->
            (cause as? co.elastic.clients.elasticsearch._types.ElasticsearchException)?.status() == HTTP_CONFLICT ||
                (cause as? ResponseException)?.response?.statusLine?.statusCode == HTTP_CONFLICT
        }

    private data class StoredEquipment(
        val equipment: Equipment,
        val seqNo: Long,
        val primaryTerm: Long,
    )

    companion object {
        private const val CLEANUP_RETRIES = 3
        private const val MAX_LIST_SIZE = 10_000
        private const val HTTP_CONFLICT = 409
    }
}

data class ElasticsearchEquipmentRepositoryHooks(
    val beforeCreateIndex: () -> Unit = {},
    val beforeCleanupDelete: () -> Unit = {},
    val beforeOccWrite: () -> Unit = {},
)

class ReservationCleanupException(
    code: String,
    equipmentId: UUID,
    cause: Throwable,
) : IllegalStateException(
    "Elasticsearch operation was applied, but reservation cleanup failed for code '$code' and equipment '$equipmentId'",
    cause,
)

data class EquipmentDocument(
    val id: String,
    val code: String,
    val name: String,
    val status: String,
) {
    fun toEquipment(): Equipment =
        Equipment.create(UUID.fromString(id), code, name, EquipmentStatus.valueOf(status))
}

data class EquipmentCodeReservationDocument(
    val code: String,
    val equipmentId: String,
)

private fun Equipment.toDocument(): EquipmentDocument =
    EquipmentDocument(id.toString(), code, name, status.name)

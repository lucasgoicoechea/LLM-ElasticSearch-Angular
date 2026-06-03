package com.example.equipment.adapter.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.example.equipment.application.EquipmentCodeConflictException
import com.example.equipment.domain.EquipmentStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpHost
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ElasticsearchEquipmentRepositoryIntegrationTest {
    @BeforeEach
    fun resetIndices() {
        repository.resetIndices()
    }

    @Test
    fun `persists crud across adapter recreation and reports missing equipment`() {
        val created = repository.create(" PUMP-01 ", " Primary Pump ", EquipmentStatus.ACTIVE)

        val restartedAdapter = ElasticsearchEquipmentRepository(client)
        assertThat(restartedAdapter.findById(created.id)).isEqualTo(created)
        assertThat(restartedAdapter.findAll()).containsExactly(created)

        val updated = restartedAdapter.update(created.id, "PUMP-02", "Backup Pump", EquipmentStatus.INACTIVE)
        assertThat(updated).isEqualTo(created.replace("PUMP-02", "Backup Pump", EquipmentStatus.INACTIVE))
        assertThat(restartedAdapter.deleteById(created.id)).isTrue()
        assertThat(restartedAdapter.findById(created.id)).isNull()
        assertThat(restartedAdapter.deleteById(created.id)).isFalse()
        assertThat(restartedAdapter.update(created.id, "MISSING", "Missing", EquipmentStatus.ACTIVE)).isNull()
    }

    @Test
    fun `preserves exact case uniqueness and rejects concurrent duplicate creates`() {
        val upper = repository.create("PUMP-01", "Upper", EquipmentStatus.ACTIVE)
        val lower = repository.create("pump-01", "Lower", EquipmentStatus.ACTIVE)
        assertThat(repository.findAll()).containsExactlyInAnyOrder(upper, lower)

        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(
                listOf(
                    Callable { runCatching { repository.create("VALVE-01", "First", EquipmentStatus.ACTIVE) } },
                    Callable { runCatching { repository.create("VALVE-01", "Second", EquipmentStatus.ACTIVE) } },
                ),
            ).map { it.get() }

            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.mapNotNull { it.exceptionOrNull() })
                .singleElement()
                .isInstanceOf(EquipmentCodeConflictException::class.java)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `rolls back reservation when create document fails`() {
        val fixedId = UUID.randomUUID()
        createEquipmentDocument(fixedId, "BLOCKER")
        val failingRepository = ElasticsearchEquipmentRepository(client) { fixedId }

        assertThatThrownBy {
            failingRepository.create("ROLLBACK-01", "Will Fail", EquipmentStatus.ACTIVE)
        }.isNotInstanceOf(EquipmentCodeConflictException::class.java)

        val recovered = repository.create("ROLLBACK-01", "Recovered", EquipmentStatus.ACTIVE)
        assertThat(recovered.code).isEqualTo("ROLLBACK-01")
    }

    @Test
    fun `transfers reservations rejects conflicting transfer and cleans reservation on delete`() {
        val first = repository.create("CODE-A", "First", EquipmentStatus.ACTIVE)
        repository.create("CODE-B", "Second", EquipmentStatus.ACTIVE)

        assertThatThrownBy {
            repository.update(first.id, "CODE-B", "Conflict", EquipmentStatus.INACTIVE)
        }.isInstanceOf(EquipmentCodeConflictException::class.java)
        assertThat(repository.findById(first.id)?.code).isEqualTo("CODE-A")

        assertThat(repository.update(first.id, "CODE-C", "Transferred", EquipmentStatus.INACTIVE)?.code)
            .isEqualTo("CODE-C")
        assertThat(repository.create("CODE-A", "Reused Old", EquipmentStatus.ACTIVE).code).isEqualTo("CODE-A")

        assertThat(repository.deleteById(first.id)).isTrue()
        assertThat(repository.create("CODE-C", "Reused Deleted", EquipmentStatus.ACTIVE).code).isEqualTo("CODE-C")
    }

    @Test
    fun `tolerates concurrent index initialization`() {
        repository.resetIndices()
        deleteIndices()
        val barrier = CyclicBarrier(2)
        val hooks = ElasticsearchEquipmentRepositoryHooks(beforeCreateIndex = { barrier.await() })
        val repositories = List(2) { ElasticsearchEquipmentRepository(client, hooks = hooks) }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val results = executor.invokeAll(repositories.map { candidate -> Callable { candidate.findAll() } })
            assertThat(results.map { it.get() }).containsExactlyInAnyOrder(emptyList(), emptyList())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `retries reservation cleanup failures and releases the code`() {
        val attempts = AtomicInteger()
        val retryingRepository = ElasticsearchEquipmentRepository(
            client,
            hooks = ElasticsearchEquipmentRepositoryHooks(
                beforeCleanupDelete = {
                    if (attempts.incrementAndGet() < 3) throw IOException("transient cleanup failure")
                },
            ),
        )
        val created = retryingRepository.create("RETRY-CLEANUP", "Retry", EquipmentStatus.ACTIVE)

        assertThat(retryingRepository.deleteById(created.id)).isTrue()
        assertThat(attempts).hasValue(3)
        assertThat(repository.create("RETRY-CLEANUP", "Recovered", EquipmentStatus.ACTIVE).code)
            .isEqualTo("RETRY-CLEANUP")
    }

    @Test
    fun `reports cleanup exhaustion after delete applied and does not silently accept stale reservation`() {
        val failingRepository = ElasticsearchEquipmentRepository(
            client,
            hooks = ElasticsearchEquipmentRepositoryHooks(
                beforeCleanupDelete = { throw IOException("persistent cleanup failure") },
            ),
        )
        val created = failingRepository.create("STALE-DELETE", "Delete", EquipmentStatus.ACTIVE)

        assertThatThrownBy { failingRepository.deleteById(created.id) }
            .isInstanceOf(ReservationCleanupException::class.java)
            .hasMessageContaining("STALE-DELETE")
        assertThat(repository.findById(created.id)).isNull()
        assertThatThrownBy { repository.create("STALE-DELETE", "Blocked", EquipmentStatus.ACTIVE) }
            .isInstanceOf(EquipmentCodeConflictException::class.java)
    }

    @Test
    fun `reports cleanup exhaustion after update applied and protects stale owner reservations`() {
        val created = repository.create("STALE-OLD", "Before", EquipmentStatus.ACTIVE)
        val failingRepository = ElasticsearchEquipmentRepository(
            client,
            hooks = ElasticsearchEquipmentRepositoryHooks(
                beforeCleanupDelete = { throw IOException("persistent cleanup failure") },
            ),
        )

        assertThatThrownBy {
            failingRepository.update(created.id, "STALE-NEW", "After", EquipmentStatus.INACTIVE)
        }.isInstanceOf(ReservationCleanupException::class.java)
            .hasMessageContaining("STALE-OLD")
        assertThat(repository.findById(created.id)?.code).isEqualTo("STALE-NEW")
        assertThatThrownBy { repository.create("STALE-OLD", "Blocked", EquipmentStatus.ACTIVE) }
            .isInstanceOf(EquipmentCodeConflictException::class.java)
    }

    @Test
    fun `keeps a reservation when cleanup sees a different owner`() {
        val created = repository.create("OWNER-CODE", "Owned", EquipmentStatus.ACTIVE)
        val replacementOwner = UUID.randomUUID()
        replaceReservationOwner("OWNER-CODE", replacementOwner)

        assertThat(repository.deleteById(created.id)).isTrue()
        assertThatThrownBy { repository.create("OWNER-CODE", "Blocked", EquipmentStatus.ACTIVE) }
            .isInstanceOf(EquipmentCodeConflictException::class.java)
    }

    @Test
    fun `allows only one occ update and one occ delete loser`() {
        val updateTarget = repository.create("OCC-UPDATE", "Before", EquipmentStatus.ACTIVE)
        val updateBarrier = CyclicBarrier(2)
        val updateRepository = ElasticsearchEquipmentRepository(
            client,
            hooks = ElasticsearchEquipmentRepositoryHooks(beforeOccWrite = { updateBarrier.await() }),
        )
        val updateResults = runConcurrently(
            { updateRepository.update(updateTarget.id, "OCC-UPDATE", "First", EquipmentStatus.ACTIVE) },
            { updateRepository.update(updateTarget.id, "OCC-UPDATE", "Second", EquipmentStatus.ACTIVE) },
        )
        assertThat(updateResults.count { it.isSuccess }).isEqualTo(1)
        assertThat(updateResults.count { it.isFailure }).isEqualTo(1)

        val deleteTarget = repository.create("OCC-DELETE", "Before", EquipmentStatus.ACTIVE)
        val deleteBarrier = CyclicBarrier(2)
        val deleteRepository = ElasticsearchEquipmentRepository(
            client,
            hooks = ElasticsearchEquipmentRepositoryHooks(beforeOccWrite = { deleteBarrier.await() }),
        )
        val deleteResults = runConcurrently(
            { deleteRepository.deleteById(deleteTarget.id) },
            { deleteRepository.deleteById(deleteTarget.id) },
        )
        assertThat(deleteResults.count { it.isSuccess }).isEqualTo(1)
        assertThat(deleteResults.count { it.isFailure }).isEqualTo(1)
    }

    private fun createEquipmentDocument(id: UUID, code: String) {
        client.create<Map<String, String>> {
            it.index(EQUIPMENT_INDEX)
                .id(id.toString())
                .document(
                    mapOf(
                        "id" to id.toString(),
                        "code" to code,
                        "name" to "Blocker",
                        "status" to EquipmentStatus.ACTIVE.name,
                    ),
                )
                .refresh(Refresh.WaitFor)
        }
    }

    private fun replaceReservationOwner(code: String, equipmentId: UUID) {
        client.index<EquipmentCodeReservationDocument> {
            it.index(EQUIPMENT_CODE_RESERVATIONS_INDEX)
                .id(reservationId(code))
                .document(EquipmentCodeReservationDocument(code, equipmentId.toString()))
                .refresh(Refresh.WaitFor)
        }
    }

    private fun deleteIndices() {
        listOf(EQUIPMENT_INDEX, EQUIPMENT_CODE_RESERVATIONS_INDEX).forEach { index ->
            if (client.indices().exists { it.index(index) }.value()) {
                client.indices().delete { it.index(index) }
            }
        }
    }

    private fun <T> runConcurrently(first: () -> T, second: () -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(2)
        return try {
            executor.invokeAll(listOf(Callable { runCatching(first) }, Callable { runCatching(second) }))
                .map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun reservationId(code: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(code.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

    companion object {
        private val container = ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.1"),
        )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .apply { start() }
        private val transport = RestClientTransport(
            org.elasticsearch.client.RestClient.builder(HttpHost.create(container.httpHostAddress)).build(),
            JacksonJsonpMapper(ObjectMapper().registerKotlinModule()),
        )
        private val client = ElasticsearchClient(transport)
        private val repository = ElasticsearchEquipmentRepository(client)

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            transport.close()
            container.stop()
        }
    }
}

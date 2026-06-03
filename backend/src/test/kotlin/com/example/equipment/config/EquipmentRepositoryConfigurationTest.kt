package com.example.equipment.config

import com.example.equipment.adapter.inmemory.InMemoryEquipmentRepository
import com.example.equipment.application.EquipmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EquipmentRepositoryConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(EquipmentRepositoryConfiguration::class.java)

    @Test
    fun `uses the in-memory repository by default`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(EquipmentRepository::class.java)
            assertThat(context.getBean(EquipmentRepository::class.java))
                .isInstanceOf(InMemoryEquipmentRepository::class.java)
        }
    }

    @Test
    fun `uses the in-memory repository when explicitly selected`() {
        contextRunner
            .withPropertyValues("equipment.repository=in-memory")
            .run { context ->
                assertThat(context).hasSingleBean(EquipmentRepository::class.java)
                assertThat(context.getBean(EquipmentRepository::class.java))
                    .isInstanceOf(InMemoryEquipmentRepository::class.java)
            }
    }

    @Test
    fun `fails fast when elasticsearch is selected before the adapter slice`() {
        contextRunner
            .withPropertyValues("equipment.repository=elasticsearch")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasRootCauseMessage(
                    "Elasticsearch repository adapter is implemented in the next slice",
                )
            }
    }
}
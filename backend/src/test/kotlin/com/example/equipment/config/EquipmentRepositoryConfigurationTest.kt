package com.example.equipment.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.example.equipment.adapter.elasticsearch.ElasticsearchEquipmentRepository
import com.example.equipment.adapter.inmemory.InMemoryEquipmentRepository
import com.example.equipment.application.EquipmentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
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
    fun `uses the elasticsearch adapter when selected`() {
        contextRunner
            .withPropertyValues("equipment.repository=elasticsearch")
            .withBean(ElasticsearchClient::class.java, { mock(ElasticsearchClient::class.java) })
            .run { context ->
                assertThat(context).hasSingleBean(EquipmentRepository::class.java)
                assertThat(context.getBean(EquipmentRepository::class.java))
                    .isInstanceOf(ElasticsearchEquipmentRepository::class.java)
            }
    }
}

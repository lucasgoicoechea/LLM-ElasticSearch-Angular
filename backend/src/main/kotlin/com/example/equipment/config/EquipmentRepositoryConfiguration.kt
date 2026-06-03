package com.example.equipment.config

import com.example.equipment.adapter.inmemory.InMemoryEquipmentRepository
import com.example.equipment.application.EquipmentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class EquipmentRepositoryConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "equipment",
        name = ["repository"],
        havingValue = "in-memory",
        matchIfMissing = true,
    )
    fun inMemoryEquipmentRepository(): EquipmentRepository = InMemoryEquipmentRepository()

    @Bean
    @ConditionalOnProperty(
        prefix = "equipment",
        name = ["repository"],
        havingValue = "elasticsearch",
    )
    fun elasticsearchEquipmentRepository(): EquipmentRepository =
        throw IllegalStateException("Elasticsearch repository adapter is implemented in the next slice")
}
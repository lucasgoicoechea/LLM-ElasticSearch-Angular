package com.example.equipment

import com.example.equipment.adapter.inmemory.InMemoryEquipmentRepository
import com.example.equipment.application.EquipmentRepository
import com.example.equipment.application.EquipmentService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class EquipmentApplication {
    @Bean
    fun equipmentRepository(): EquipmentRepository = InMemoryEquipmentRepository()

    @Bean
    fun equipmentService(repository: EquipmentRepository): EquipmentService =
        EquipmentService(repository)
}

fun main(args: Array<String>) {
    runApplication<EquipmentApplication>(*args)
}

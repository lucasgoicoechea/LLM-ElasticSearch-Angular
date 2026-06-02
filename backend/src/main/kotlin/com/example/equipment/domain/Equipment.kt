package com.example.equipment.domain

import java.util.UUID

@ConsistentCopyVisibility
data class Equipment private constructor(
    val id: UUID,
    val code: String,
    val name: String,
    val status: EquipmentStatus,
) {
    fun replace(code: String, name: String, status: EquipmentStatus): Equipment =
        create(id = id, code = code, name = name, status = status)

    companion object {
        private const val MAX_CODE_LENGTH = 64
        private const val MAX_NAME_LENGTH = 120

        fun create(id: UUID, code: String, name: String, status: EquipmentStatus): Equipment {
            val normalizedCode = code.trim()
            val normalizedName = name.trim()

            require(normalizedCode.length in 1..MAX_CODE_LENGTH) {
                "Equipment code must contain between 1 and $MAX_CODE_LENGTH characters"
            }
            require(normalizedName.length in 1..MAX_NAME_LENGTH) {
                "Equipment name must contain between 1 and $MAX_NAME_LENGTH characters"
            }

            return Equipment(
                id = id,
                code = normalizedCode,
                name = normalizedName,
                status = status,
            )
        }
    }
}

enum class EquipmentStatus {
    ACTIVE,
    INACTIVE,
}

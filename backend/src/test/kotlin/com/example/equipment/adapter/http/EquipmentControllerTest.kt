package com.example.equipment.adapter.http

import com.example.equipment.application.EquipmentService
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class EquipmentControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var service: EquipmentService

    @BeforeEach
    fun clearCatalog() {
        service.list().forEach { equipment ->
            service.delete(equipment.id)
        }
    }

    @Test
    fun `creates equipment and returns its representation`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "  PUMP-01  ", name = "  Main Pump  ", status = "ACTIVE")
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.code") { value("PUMP-01") }
                jsonPath("$.name") { value("Main Pump") }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun `lists every equipment representation`() {
        createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")
        createEquipment(code = "VALVE-02", name = "Safety Valve", status = "INACTIVE")

        mockMvc.get("/internal/equipment")
            .andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(2))
                jsonPath("$[0].code") { value("PUMP-01") }
                jsonPath("$[1].code") { value("VALVE-02") }
            }
    }

    @Test
    fun `lists an empty catalog`() {
        mockMvc.get("/internal/equipment")
            .andExpect {
                status { isOk() }
                content { json("[]") }
            }
    }

    @Test
    fun `retrieves equipment by id`() {
        val id = createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")

        mockMvc.get("/internal/equipment/{id}", id)
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.code") { value("PUMP-01") }
                jsonPath("$.name") { value("Main Pump") }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun `updates mutable fields while preserving id`() {
        val id = createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")

        mockMvc.put("/internal/equipment/{id}", id) {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "PUMP-02", name = "Backup Pump", status = "INACTIVE")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.code") { value("PUMP-02") }
                jsonPath("$.name") { value("Backup Pump") }
                jsonPath("$.status") { value("INACTIVE") }
            }
    }

    @Test
    fun `deletes existing equipment without a response body`() {
        val id = createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")

        mockMvc.delete("/internal/equipment/{id}", id)
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }

        mockMvc.get("/internal/equipment/{id}", id)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("EQUIPMENT_NOT_FOUND") }
            }
    }

    @Test
    fun `returns validation envelope and field errors for invalid fields`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "   ", name = "x".repeat(121), status = "ACTIVE")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.message") { value("Request validation failed") }
                jsonPath("$.errors", hasSize<Any>(2))
                jsonPath("$.errors[?(@.field == 'code')].message") { value("must not be blank") }
                jsonPath("$.errors[?(@.field == 'name')].message") {
                    value("size must be between 0 and 120")
                }
            }
    }

    @Test
    fun `rejects missing required fields`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"PUMP-01","status":"ACTIVE"}"""
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].message") { value("is required") }
            }
    }

    @Test
    fun `rejects unknown request fields`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """{"code":"PUMP-01","name":"Main Pump","status":"ACTIVE","location":"Plant 1"}"""
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors[0].field") { value("location") }
                jsonPath("$.errors[0].message") { value("Unknown field") }
            }
    }

    @Test
    fun `rejects malformed UUID path variables`() {
        mockMvc.get("/internal/equipment/{id}", "not-a-uuid")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors[0].field") { value("id") }
                jsonPath("$.errors[0].message") { value("must be a valid UUID") }
            }
    }

    @Test
    fun `rejects unsupported statuses`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "PUMP-01", name = "Main Pump", status = "BROKEN")
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors[0].field") { value("status") }
                jsonPath("$.errors[0].message") { value("has an invalid value") }
            }
    }

    @Test
    fun `validates text length after trimming`() {
        val maxLengthCode = "x".repeat(64)
        val maxLengthName = "x".repeat(120)

        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "  $maxLengthCode  ", name = "  $maxLengthName  ", status = "ACTIVE")
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.code") { value(maxLengthCode) }
                jsonPath("$.name") { value(maxLengthName) }
            }
    }

    @Test
    fun `returns not found envelope for missing equipment`() {
        val missingId = "2f1d80ec-8f20-42e4-b06c-b0e0e08da66c"

        mockMvc.get("/internal/equipment/{id}", missingId)
            .andExpect {
                status { isNotFound() }
                content {
                    json(
                        """{"code":"EQUIPMENT_NOT_FOUND","message":"Equipment not found: $missingId"}""",
                        JsonCompareMode.STRICT,
                    )
                }
                jsonPath("$.code") { value("EQUIPMENT_NOT_FOUND") }
                jsonPath("$.message") { value("Equipment not found: $missingId") }
                jsonPath("$.errors") { doesNotExist() }
            }
    }

    @Test
    fun `normalizes top level array validation errors to the request field`() {
        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = "[]"
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors[0].field") { value("request") }
                jsonPath("$.errors[0].message") { value("is required") }
            }
    }

    @Test
    fun `returns not found envelope when updating or deleting missing equipment`() {
        val missingId = "2f1d80ec-8f20-42e4-b06c-b0e0e08da66c"

        mockMvc.put("/internal/equipment/{id}", missingId) {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")
        }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("EQUIPMENT_NOT_FOUND") }
            }

        mockMvc.delete("/internal/equipment/{id}", missingId)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("EQUIPMENT_NOT_FOUND") }
            }
    }

    @Test
    fun `returns conflict envelope when creating a duplicated code`() {
        createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")

        mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "  PUMP-01  ", name = "Backup Pump", status = "INACTIVE")
        }
            .andExpect {
                status { isConflict() }
                content {
                    json(
                        """{"code":"EQUIPMENT_CODE_CONFLICT","message":"Equipment code already exists: PUMP-01"}""",
                        JsonCompareMode.STRICT,
                    )
                }
                jsonPath("$.code") { value("EQUIPMENT_CODE_CONFLICT") }
                jsonPath("$.message") { value("Equipment code already exists: PUMP-01") }
                jsonPath("$.errors") { doesNotExist() }
            }
    }

    @Test
    fun `returns conflict envelope when updating to a duplicated code`() {
        createEquipment(code = "PUMP-01", name = "Main Pump", status = "ACTIVE")
        val id = createEquipment(code = "VALVE-02", name = "Safety Valve", status = "INACTIVE")

        mockMvc.put("/internal/equipment/{id}", id) {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = "PUMP-01", name = "Replacement Valve", status = "ACTIVE")
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("EQUIPMENT_CODE_CONFLICT") }
                jsonPath("$.message") { value("Equipment code already exists: PUMP-01") }
            }
    }

    private fun createEquipment(code: String, name: String, status: String): String {
        val response = mockMvc.post("/internal/equipment") {
            contentType = MediaType.APPLICATION_JSON
            content = payload(code = code, name = name, status = status)
        }
            .andExpect {
                status { isCreated() }
            }
            .andReturn()
            .response

        return objectMapper.readTree(response.contentAsString).get("id").asText()
    }

    private fun payload(code: String, name: String, status: String): String =
        objectMapper.writeValueAsString(
            mapOf(
                "code" to code,
                "name" to name,
                "status" to status,
            ),
        )
}

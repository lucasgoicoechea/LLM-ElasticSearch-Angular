package com.example.equipment

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunnerSmokeTest {
    @Test
    fun `runs tests on a supported Java runtime`() {
        assertTrue(
            Runtime.version().feature() >= 17,
            "The backend test runner requires Java 17 or newer",
        )
    }
}

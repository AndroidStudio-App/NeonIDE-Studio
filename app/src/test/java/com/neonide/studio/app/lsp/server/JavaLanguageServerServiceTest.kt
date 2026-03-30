package com.neonide.studio.app.lsp.server

import org.junit.Test
import org.junit.Assert.*

class JavaLanguageServerServiceTest {

    @Test
    fun testServiceCreation() {
        val service = JavaLanguageServerService()
        assertNotNull("Service should be created", service)
    }
}
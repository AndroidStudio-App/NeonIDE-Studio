package com.neonide.studio.app.editor.diagnostic

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticMappingTest {

    @Test
    fun testSeverityMapping() {
        assertEquals(DiagnosticRegion.SEVERITY_ERROR, mapSeverity(DiagnosticSeverity.Error))
        assertEquals(DiagnosticRegion.SEVERITY_WARNING, mapSeverity(DiagnosticSeverity.Warning))
        assertEquals(DiagnosticRegion.SEVERITY_NONE, mapSeverity(DiagnosticSeverity.Information))
        assertEquals(DiagnosticRegion.SEVERITY_TYPO, mapSeverity(DiagnosticSeverity.Hint))
    }

    private fun mapSeverity(severity: DiagnosticSeverity?): Short {
        // Placeholder for logic extracted from activity
        return when (severity) {
            org.eclipse.lsp4j.DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
            org.eclipse.lsp4j.DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
            org.eclipse.lsp4j.DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_NONE
            org.eclipse.lsp4j.DiagnosticSeverity.Hint -> DiagnosticRegion.SEVERITY_TYPO
            else -> DiagnosticRegion.SEVERITY_NONE
        }
    }
}

package com.neonide.studio.app.lsp;

import org.junit.Test;
import static org.junit.Assert.*;

public class LspDependencyTest {
    @Test
    public void testLsp4jAvailable() {
        try {
            Class.forName("org.eclipse.lsp4j.jsonrpc.Launcher");
            assertTrue("LSP4J Launcher class found", true);
        } catch (ClassNotFoundException e) {
            fail("LSP4J library not found on classpath: " + e.getMessage());
        }
    }
}

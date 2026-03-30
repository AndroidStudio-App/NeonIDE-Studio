package com.neonide.studio.app.lsp;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.CompletableFuture;

public class LspClient implements LanguageClient {

    private DiagnosticsListener diagnosticsListener;

    public interface DiagnosticsListener {
        void onDiagnosticsPublished(PublishDiagnosticsParams params);
    }

    public void setDiagnosticsListener(DiagnosticsListener listener) {
        this.diagnosticsListener = listener;
    }

    @Override
    public void telemetryEvent(Object object) {
        // TODO: Implement telemetry
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        if (diagnosticsListener != null) {
            diagnosticsListener.onDiagnosticsPublished(diagnostics);
        }
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        // TODO: Implement showing messages to user
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        // TODO: Implement showing message requests
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        // TODO: Implement logging
    }
}
package com.neonide.studio.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.neonide.studio.app.lsp.LspManager
import com.neonide.studio.app.lsp.LspStatus

/**
 * ViewModel for managing the state and lifecycle of components within SoraEditorActivityK.
 */
class EditorViewModel : ViewModel() {

    /**
     * Manager for Language Server Protocol integration.
     */
    val lspManager = LspManager().apply {
        setStatusListener { status ->
            setLspStatus(status)
        }
    }

    private val _connectionStatus = MutableLiveData<LspStatus>(LspStatus.Disconnected)
    
    /**
     * Observable status of the LSP connection.
     */
    val connectionStatus: LiveData<LspStatus> = _connectionStatus

    /**
     * Update the current LSP connection status.
     */
    fun setLspStatus(status: LspStatus) {
        _connectionStatus.postValue(status)
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure the LSP manager is shut down when the ViewModel is cleared.
        lspManager.shutdown()
    }
}

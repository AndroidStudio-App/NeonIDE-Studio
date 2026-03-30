package com.neonide.studio.app.bottomsheet.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Shared state for the editor bottom sheet tabs.
 * Keep it simple (Strings and lists) to avoid dragging lots of ACS internals.
 */
class BottomSheetViewModel : ViewModel() {

    // Build output (gradle)
    private val _buildOutput = MutableLiveData("")
    val buildOutput: LiveData<String> = _buildOutput

    // App logs (logcat)
    private val _appLogs = MutableLiveData("")
    val appLogs: LiveData<String> = _appLogs

    // IDE logs (internal)
    private val _ideLogs = MutableLiveData("")
    val ideLogs: LiveData<String> = _ideLogs

    // Diagnostics placeholder
    private val _diagnostics = MutableLiveData<List<String>>(emptyList())
    val diagnostics: LiveData<List<String>> = _diagnostics

    // Search results
    private val _searchResults = MutableLiveData<List<String>>(emptyList())
    val searchResults: LiveData<List<String>> = _searchResults

    // Navigation results (Definition / References)
    private val _navigationResults = MutableLiveData<List<NavigationItem>>(emptyList())
    val navigationResults: LiveData<List<NavigationItem>> = _navigationResults

    fun setBuildOutput(text: String) = _buildOutput.postValue(text)
    fun appendBuildOutput(line: String) = _buildOutput.postValue((_buildOutput.value ?: "") + line + "\n")

    fun setAppLogs(text: String) = _appLogs.postValue(text)
    fun setIdeLogs(text: String) = _ideLogs.postValue(text)

    fun setDiagnostics(items: List<String>) = _diagnostics.postValue(items)
    fun setSearchResults(items: List<String>) = _searchResults.postValue(items)
    fun setNavigationResults(items: List<NavigationItem>) = _navigationResults.postValue(items)
}

data class NavigationItem(
    val uri: String,
    val line: Int,
    val character: Int,
    val displayText: String
)
package com.neonide.studio.app.home.clone

/**
 * Immutable UI state for the Clone Repository dialog.
 * Holds all form field values, validation errors, toggle states, and progress info.
 */
data class CloneUiState(
    val urlText: String = "",
    val urlError: String? = null,
    val destText: String = "",
    val destError: String? = null,
    val repoNameText: String = "",
    val repoNameError: String? = null,
    val branchText: String = "",
    val usernameText: String = "",
    val usernameError: String? = null,
    val passwordText: String = "",
    val passwordError: String? = null,
    val depthText: String = "1",
    val depthError: String? = null,
    val useCreds: Boolean = false,
    val shallowClone: Boolean = false,
    val singleBranch: Boolean = true,
    val recurseSubmodules: Boolean = false,
    val shallowSubmodules: Boolean = false,
    val openAfterClone: Boolean = true,
    val isCloning: Boolean = false,
    val cloneStatus: String? = null,
    val lastProgressPercent: Int? = null,
    val lastProgressBytes: Long? = null,
    val lastProgressSpeedBps: Long? = null,
) {
    /** Convenience: is the branch toggle logically enabled? */
    val isBranchEnabled: Boolean
        get() = branchText.isNotBlank() || singleBranch
}

/**
 * User actions triggered by the Clone Repository dialog UI.
 * Each callback maps to a specific state mutation or side-effect.
 */
data class CloneActions(
    val onUrlChange: (String) -> Unit,
    val onDestChange: (String) -> Unit,
    val onRepoNameChange: (String) -> Unit,
    val onBranchChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onDepthChange: (String) -> Unit,
    val onUseCredsChange: (Boolean) -> Unit,
    val onShallowCloneChange: (Boolean) -> Unit,
    val onSingleBranchChange: (Boolean) -> Unit,
    val onRecurseSubmodulesChange: (Boolean) -> Unit,
    val onShallowSubmodulesChange: (Boolean) -> Unit,
    val onOpenAfterCloneChange: (Boolean) -> Unit,
    val onBrowseDest: () -> Unit,
    val onClone: () -> Unit,
    val onStopOrCancel: () -> Unit,
)

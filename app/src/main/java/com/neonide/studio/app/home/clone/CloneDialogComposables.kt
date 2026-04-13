package com.neonide.studio.app.home.clone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R
import java.util.Locale

/**
 * Root composable for the Clone Repository dialog content.
 * Displays sectioned cards (Source, Destination, Options) with expandable toggles.
 */
@Composable
fun CloneDialogContent(
    state: CloneUiState,
    actions: CloneActions,
) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        // Header
        item(key = "header_title", contentType = "header") {
            Text(
                text = stringResource(R.string.acs_clone_git_repository),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item(key = "header_subtitle", contentType = "header") {
            Text(
                text = stringResource(R.string.acs_clone_git_repository_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        // Progress indicator (visible during cloning)
        if (state.isCloning) {
            item(key = "progress", contentType = "progress") {
                CloneProgressIndicator(state)
            }
        }

        // Source section
        item(key = "section_source", contentType = "section_card") {
            SectionCard(
                iconRes = "link",
                label = "Source",
                isPrimary = true,
            ) {
                CloneUrlField(state.urlText, actions.onUrlChange, state.urlError)
            }
        }

        // Destination section
        item(key = "section_destination", contentType = "section_card") {
            SectionCard(
                iconRes = "Folder",
                label = "Destination",
            ) {
                CloneRepoNameField(state.repoNameText, actions.onRepoNameChange, state.repoNameError)
                Spacer(Modifier.height(8.dp))
                CloneDestField(state.destText, actions.onDestChange, state.destError, actions.onBrowseDest)
            }
        }

        // Options section
        item(key = "section_options", contentType = "section_options") {
            SectionCard(
                iconRes = "Git",
                label = "Options",
            ) {
                // Credentials toggle
                ToggleRow(
                    title = stringResource(R.string.acs_clone_use_credentials),
                    description = "Authenticate with username & password",
                    checked = state.useCreds,
                    onCheckedChange = actions.onUseCredsChange,
                )
                AnimatedVisibility(
                    visible = state.useCreds,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        CloneUsernameField(state.usernameText, actions.onUsernameChange, state.usernameError)
                        Spacer(Modifier.height(4.dp))
                        ClonePasswordField(state.passwordText, actions.onPasswordChange, state.passwordError)
                        Spacer(Modifier.height(4.dp))
                        InfoBadge(text = stringResource(R.string.acs_clone_credentials_hint))
                    }
                }

                Separator()

                // Branch toggle
                ToggleRow(
                    title = "Specific branch",
                    description = "Single Branch",
                    checked = state.isBranchEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            actions.onBranchChange("main")
                            actions.onSingleBranchChange(true)
                        } else {
                            actions.onBranchChange("")
                            actions.onSingleBranchChange(false)
                        }
                    },
                )
                AnimatedVisibility(
                    visible = state.isBranchEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        CloneBranchField(state.branchText, actions.onBranchChange)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SingleBranchChip(
                                checked = state.singleBranch,
                                onCheckedChange = actions.onSingleBranchChange,
                            )
                        }
                    }
                }

                Separator()

                // Shallow clone toggle
                ToggleRow(
                    title = stringResource(R.string.acs_clone_shallow),
                    description = "Limit commit history to save time & space",
                    checked = state.shallowClone,
                    onCheckedChange = actions.onShallowCloneChange,
                )
                AnimatedVisibility(
                    visible = state.shallowClone,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        CloneDepthField(state.depthText, actions.onDepthChange, state.depthError)
                        Spacer(Modifier.height(4.dp))
                        InfoBadge(text = "Faster clone with limited history")
                    }
                }

                Separator()

                // Submodules toggle
                ToggleRow(
                    title = stringResource(R.string.acs_clone_recurse_submodules),
                    description = "Clone recurse submodules ",
                    checked = state.recurseSubmodules,
                    onCheckedChange = actions.onRecurseSubmodulesChange,
                )
                AnimatedVisibility(
                    visible = state.recurseSubmodules,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        ShallowSubmodulesChip(
                            checked = state.shallowSubmodules,
                            onCheckedChange = actions.onShallowSubmodulesChange,
                        )
                    }
                }

                Separator()

                // Open after clone toggle
                ToggleRow(
                    title = stringResource(R.string.acs_clone_open_after),
                    description = "Launch the editor after",
                    checked = state.openAfterClone,
                    onCheckedChange = actions.onOpenAfterCloneChange,
                )
            }
        }

        item(key = "footer_spacer", contentType = "footer") {
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Section Card
// ---------------------------------------------------------------------------

/**
 * A visually grouped section card with an icon, label, and content.
 */
@Composable
private fun SectionCard(
    iconRes: String,
    label: String,
    isPrimary: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bgColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                // Icon placeholder — uses Material Icons font
                Text(
                    text = iconRes,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Clean input field (underlined, no outline)
// ---------------------------------------------------------------------------

@Composable
private fun CleanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            trailingIcon = trailingIcon,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                errorIndicatorColor = MaterialTheme.colorScheme.error,
            ),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// URL field (auto-focused)
// ---------------------------------------------------------------------------

@Composable
private fun CloneUrlField(
    urlText: String,
    onUrlChange: (String) -> Unit,
    urlError: String?,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    CleanTextField(
        value = urlText,
        onValueChange = onUrlChange,
        label = stringResource(R.string.acs_clone_repo_url),
        placeholder = "https://github.com/user/repo.git",
        isError = urlError != null,
        errorMessage = urlError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        focusRequester = focusRequester,
    )
}

// ---------------------------------------------------------------------------
// Repo name field
// ---------------------------------------------------------------------------

@Composable
private fun CloneRepoNameField(
    repoNameText: String,
    onRepoNameChange: (String) -> Unit,
    repoNameError: String?,
) {
    CleanTextField(
        value = repoNameText,
        onValueChange = onRepoNameChange,
        label = stringResource(R.string.acs_clone_repo_name),
        isError = repoNameError != null,
        errorMessage = repoNameError,
    )
}

// ---------------------------------------------------------------------------
// Destination path field with browse button
// ---------------------------------------------------------------------------

@Composable
private fun CloneDestField(
    destText: String,
    onDestChange: (String) -> Unit,
    destError: String?,
    onBrowseDest: () -> Unit,
) {
    CleanTextField(
        value = destText,
        onValueChange = onDestChange,
        label = stringResource(R.string.acs_clone_destination),
        isError = destError != null,
        errorMessage = destError,
        trailingIcon = {
            IconButton(onClick = onBrowseDest) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.browse),
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Branch field
// ---------------------------------------------------------------------------

@Composable
private fun CloneBranchField(
    branchText: String,
    onBranchChange: (String) -> Unit,
) {
    CleanTextField(
        value = branchText,
        onValueChange = onBranchChange,
        label = stringResource(R.string.acs_clone_branch),
        placeholder = "main, develop, etc.",
    )
}

// ---------------------------------------------------------------------------
// Username field
// ---------------------------------------------------------------------------

@Composable
private fun CloneUsernameField(
    usernameText: String,
    onUsernameChange: (String) -> Unit,
    usernameError: String?,
) {
    CleanTextField(
        value = usernameText,
        onValueChange = onUsernameChange,
        label = stringResource(R.string.acs_clone_username),
        placeholder = stringResource(R.string.acs_clone_username),
        isError = usernameError != null,
        errorMessage = usernameError,
    )
}

// ---------------------------------------------------------------------------
// Password field
// ---------------------------------------------------------------------------

@Composable
private fun ClonePasswordField(
    passwordText: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
) {
    CleanTextField(
        value = passwordText,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.acs_clone_password),
        placeholder = stringResource(R.string.acs_clone_password),
        visualTransformation = PasswordVisualTransformation(),
        isError = passwordError != null,
        errorMessage = passwordError,
    )
}

// ---------------------------------------------------------------------------
// Depth field (shallow clone)
// ---------------------------------------------------------------------------

@Composable
private fun CloneDepthField(
    depthText: String,
    onDepthChange: (String) -> Unit,
    depthError: String?,
) {
    CleanTextField(
        value = depthText,
        onValueChange = onDepthChange,
        label = stringResource(R.string.acs_clone_depth),
        placeholder = "1",
        isError = depthError != null,
        errorMessage = depthError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

// ---------------------------------------------------------------------------
// Toggle row (title + description + switch)
// ---------------------------------------------------------------------------

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// ---------------------------------------------------------------------------
// Chip: --single-branch
// ---------------------------------------------------------------------------

@Composable
private fun SingleBranchChip(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bgColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "--single-branch",
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Chip: Shallow submodules
// ---------------------------------------------------------------------------

@Composable
private fun ShallowSubmodulesChip(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bgColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.acs_clone_shallow_submodules),
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Separator
// ---------------------------------------------------------------------------

@Composable
private fun Separator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    )
}

// ---------------------------------------------------------------------------
// Info badge
// ---------------------------------------------------------------------------

@Composable
private fun InfoBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "\u2139\uFE0F",
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Progress indicator (shown during clone)
// ---------------------------------------------------------------------------

@Composable
private fun CloneProgressIndicator(state: CloneUiState) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { (state.lastProgressPercent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        state.cloneStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val progressParts = remember(
            state.lastProgressPercent,
            state.lastProgressBytes,
            state.lastProgressSpeedBps,
        ) {
            buildList {
                state.lastProgressPercent?.let { add("$it%") }
                state.lastProgressBytes?.let { add(formatBytes(it)) }
                state.lastProgressSpeedBps?.let { add("${formatBytes(it)}/s") }
            }
        }
        if (progressParts.isNotEmpty()) {
            Text(
                text = progressParts.joinToString(" \u2022 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Utility: format bytes to human-readable string
// ---------------------------------------------------------------------------

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val b = bytes.toDouble()
    return when {
        b >= gb -> String.format(Locale.US, "%.2f GB", b / gb)
        b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
        b >= kb -> String.format(Locale.US, "%.1f KB", b / kb)
        else -> "$bytes B"
    }
}

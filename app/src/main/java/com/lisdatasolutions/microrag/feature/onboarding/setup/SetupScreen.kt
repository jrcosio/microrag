package com.lisdatasolutions.microrag.feature.onboarding.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataIconTile
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelStatusRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStepIndicator
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.SetupRowAction
import com.lisdatasolutions.microrag.domain.model.SetupStepResolver
import org.koin.compose.viewmodel.koinViewModel

/** Callbacks of the stateless [SetupScreen]. */
data class SetupActions(
    val onBack: () -> Unit = {},
    val onRowClick: (ModelId) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
)

/** P02 with its Activity-bound side effects: notification permission, file picker, settings, navigation. */
@Composable
fun SetupRoute(onBack: () -> Unit, onCompleted: () -> Unit, viewModel: SetupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var needsPermission by rememberSaveable { mutableStateOf(needsNotificationPermission(context)) }
    var pickTarget by rememberSaveable { mutableStateOf<ModelId?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        needsPermission = needsNotificationPermission(context)
        viewModel.onNotificationPermissionResult()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickTarget?.let { viewModel.onFilePicked(it, uri?.toString()) }
        pickTarget = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        needsPermission = needsNotificationPermission(context)
        viewModel.onResumeVisible()
    }
    LaunchedEffect(viewModel) {
        viewModel.setupEvents.collect { event ->
            when (event) {
                SetupEvent.RequestNotificationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationPermissionResult()
                }
                SetupEvent.OpenStorageSettings -> context.startActivity(
                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                is SetupEvent.PickFile -> {
                    pickTarget = event.target
                    picker.launch(arrayOf("*/*"))
                }
                SetupEvent.NavigateHome -> onCompleted()
            }
        }
    }
    SetupScreen(
        state = state,
        needsNotificationPermission = needsPermission,
        actions = SetupActions(
            onBack = onBack,
            onRowClick = viewModel::onRowClick,
            onContinue = viewModel::onContinue,
            onDownload = { viewModel.onConfirmDownload(needsPermission) },
            onPickFile = viewModel::onPickFile,
            onDismissConfirmation = viewModel::onDismissConfirmation,
        ),
    )
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** P02 Preparación inicial (PNG 02, contracts/onboarding-ui.md): one screen whose step follows the real state. */
@Composable
fun SetupScreen(
    state: SetupUiState,
    needsNotificationPermission: Boolean,
    actions: SetupActions,
    modifier: Modifier = Modifier,
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val typography = LisdataTheme.typography.material
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LisdataTopBar(title = stringResource(R.string.setup_title), onBack = actions.onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
        ) {
            LisdataStepIndicator(step = state.step, total = SetupStepResolver.TOTAL_STEPS, modifier = Modifier.padding(top = spacing.space2))
            Text(
                stringResource(R.string.setup_headline),
                style = typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = spacing.space4).semantics { heading() },
            )
            Text(stringResource(R.string.setup_intro), style = typography.bodyLarge, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.space2))
            state.rows.forEach { row ->
                LisdataModelStatusRow(
                    icon = row.id.iconRes(),
                    name = stringResource(row.id.nameRes()),
                    purpose = stringResource(row.id.purposeRes()),
                    statusText = row.status.chipText(),
                    status = row.status.statusKind(),
                    progress = row.status.progress(),
                    detail = row.status.detailText(row.id),
                    accessibilityText = row.status.accessibilityText(row.id),
                    onClick = if (row.action != SetupRowAction.NONE) ({ actions.onRowClick(row.id) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StorageCard(state)
            if (needsNotificationPermission && state.downloadUnderway) {
                LisdataInfoCard(icon = LisdataIcons.Warning, title = stringResource(R.string.setup_notifications_denied))
            }
            LisdataInfoCard(
                icon = LisdataIcons.ShieldCheck,
                title = stringResource(R.string.setup_privacy_title),
                body = stringResource(R.string.setup_privacy_body),
            )
            Spacer(Modifier.height(spacing.space4))
        }
        ContinueBar(state, actions.onContinue)
    }
    state.confirmation?.let { confirmation ->
        DownloadConfirmationDialog(
            confirmation = confirmation,
            needsNotificationPermission = needsNotificationPermission,
            onDownload = actions.onDownload,
            onPickFile = actions.onPickFile,
            onDismiss = actions.onDismissConfirmation,
        )
    }
}

@Composable
private fun StorageCard(state: SetupUiState) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    LisdataCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            LisdataIconTile(icon = LisdataIcons.Database, style = IconTileStyle.Light)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(stringResource(R.string.setup_storage_title), style = LisdataTheme.typography.material.titleMedium, color = colors.textPrimary)
                Text(stringResource(R.string.setup_storage_body), style = LisdataTheme.typography.material.bodyMedium, color = colors.textSecondary)
            }
            LisdataStatusChip(text = state.storage.chipText(), kind = state.storage.statusKind())
        }
    }
}

@Composable
private fun ContinueBar(state: SetupUiState, onContinue: () -> Unit) {
    val spacing = LisdataTheme.spacing
    val blockerText = state.blocker?.text()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding()
            .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space3),
        verticalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        if (blockerText != null) {
            Text(
                blockerText,
                style = LisdataTheme.typography.material.bodyMedium,
                color = LisdataTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LisdataGradientButton(
            text = stringResource(R.string.setup_continue),
            onClick = onContinue,
            enabled = state.continueEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (blockerText != null) stateDescription = blockerText },
        )
    }
}

package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomSheet
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataFactRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSheetContent
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.feature.onboarding.setup.bytesText
import com.lisdatasolutions.microrag.feature.onboarding.setup.detailText

/** Characters of the SHA-256 the sheet shows (S2); TalkBack reads them as «empieza por …». */
private const val SHORT_HASH = 12

/** S2: the read-only sheet of a model. It never verifies, downloads or deletes (P15). */
@Composable
fun ModelSheet(model: ModelOverview, onDismiss: () -> Unit, onCompleteSetup: () -> Unit) {
    LisdataBottomSheet(onDismiss = onDismiss) { ModelSheetBody(model, onCompleteSetup) }
}

/** The sheet's content without its window, for previews and tests. */
@Composable
fun ModelSheetContent(model: ModelOverview, onCompleteSetup: () -> Unit, modifier: Modifier = Modifier) {
    LisdataSheetContent(modifier = modifier) { ModelSheetBody(model, onCompleteSetup) }
}

@Composable
private fun ColumnScope.ModelSheetBody(model: ModelOverview, onCompleteSetup: () -> Unit) {
    val colors = LisdataTheme.colors
    val typography = LisdataTheme.typography.material
    val ready = model.status is ModelSetupStatus.Ready
    Column {
        Text(
            text = model.displayName,
            style = typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(if (model.id == ModelId.GEMMA4_E2B) R.string.settings_model_answers else R.string.settings_model_embeddings),
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
    Column {
        LisdataFactRow(stringResource(R.string.settings_sheet_file), model.fileName)
        LisdataFactRow(stringResource(R.string.settings_sheet_size), bytesText(model.sizeBytes))
        val shortHash = model.sha256.take(SHORT_HASH)
        LisdataFactRow(
            label = stringResource(R.string.settings_sheet_sha),
            value = "$shortHash…",
            accessibilityText = stringResource(R.string.settings_sheet_sha_a11y, shortHash),
        )
        model.dimensions?.let { LisdataFactRow(stringResource(R.string.settings_sheet_dimensions), it.toString()) }
        LisdataFactRow(
            stringResource(R.string.settings_sheet_processor),
            (model.status as? ModelSetupStatus.Ready)?.backend?.name ?: stringResource(R.string.settings_sheet_none),
        )
        LisdataFactRow(stringResource(R.string.settings_sheet_status)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space1)) {
                val chip = model.chip()
                LisdataStatusChip(text = chip.text(), kind = chip.kind())
                if (!ready) {
                    model.status.detailText(model.id)?.let {
                        Text(text = it, style = typography.bodyMedium, color = colors.textSecondary)
                    }
                }
            }
        }
    }
    if (!ready) {
        LisdataGradientButton(
            text = stringResource(R.string.chat_models_action),
            onClick = onCompleteSetup,
            modifier = Modifier.fillMaxWidth().padding(top = LisdataTheme.spacing.space2),
        )
    }
}

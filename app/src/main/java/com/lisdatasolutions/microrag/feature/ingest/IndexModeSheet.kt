package com.lisdatasolutions.microrag.feature.ingest

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomSheet
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCardGroup
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.rag.IndexMode

/** The three modes in the order of wireframe §4, each with its subtitle. */
val INDEX_MODES = listOf(IndexMode.BOTH, IndexMode.TEXT, IndexMode.VISUAL)

@StringRes
fun IndexMode.title(): Int = when (this) {
    IndexMode.BOTH -> R.string.import_mode_both
    IndexMode.TEXT -> R.string.import_mode_text
    IndexMode.VISUAL -> R.string.import_mode_visual
}

@StringRes
fun IndexMode.subtitle(): Int = when (this) {
    IndexMode.BOTH -> R.string.import_mode_both_subtitle
    IndexMode.TEXT -> R.string.import_mode_text_subtitle
    IndexMode.VISUAL -> R.string.import_mode_visual_subtitle
}

/** The row's subtitle: «Ambos (texto y páginas)», «Texto» or «Visual». */
@StringRes
fun IndexMode.summary(): Int = if (this == IndexMode.BOTH) R.string.import_mode_both_summary else title()

/**
 * «Modo de los PDF» (wireframe §5, contract U8): the three modes as a radio group; choosing one applies it to the
 * files picked next and closes the sheet. Images are always pages, as the note says.
 */
@Composable
fun IndexModeSheet(selected: IndexMode, onSelect: (IndexMode) -> Unit, onDismiss: () -> Unit) {
    val spacing = LisdataTheme.spacing
    LisdataBottomSheet(onDismiss = onDismiss, modifier = Modifier.testTag("import_mode_sheet")) {
        Text(
            text = stringResource(R.string.ingest_mode_row),
            style = LisdataTheme.typography.material.titleLarge,
            color = LisdataTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space3).semantics { heading() },
        )
        LisdataCardGroup(modifier = Modifier.selectableGroup()) {
            INDEX_MODES.forEachIndexed { position, mode ->
                if (position > 0) LisdataDivider()
                LisdataRadioRow(
                    title = stringResource(mode.title()),
                    subtitle = stringResource(mode.subtitle()),
                    selected = mode == selected,
                    onSelect = { onSelect(mode) },
                )
            }
        }
        Text(
            text = stringResource(R.string.import_mode_images_note),
            style = LisdataTheme.typography.material.bodyMedium,
            color = LisdataTheme.colors.textSecondary,
            modifier = Modifier.padding(top = spacing.space3),
        )
    }
}

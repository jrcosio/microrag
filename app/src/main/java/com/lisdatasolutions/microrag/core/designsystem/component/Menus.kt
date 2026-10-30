package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

/** The «⋯» of a top bar («Más opciones»): a menu of [items], each a label and its action (F08 G17, F11 G13). */
@Composable
fun LisdataOverflowMenu(
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            LisdataIcon(LisdataIcons.MoreHorizontal, contentDescription = stringResource(R.string.action_more_options), tint = LisdataTheme.colors.textPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LisdataTheme.colors.surface) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, color = LisdataTheme.colors.textPrimary) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

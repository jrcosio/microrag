package com.lisdatasolutions.microrag.feature.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataProgressRing
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcon
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import org.koin.compose.koinInject

/** Back and «✕» close the viewer; the page comes from the route, so it survives a rotation (research R15). */
@Composable
fun PageViewerRoute(
    documentId: Long,
    page: Int,
    documentName: String,
    image: Boolean,
    onClose: () -> Unit,
    pageImages: PageImageLoader = koinInject(),
) {
    val state by produceState<PageImageState>(PageImageState.Loading, documentId, page) {
        value = pageImages.page(documentId, page)?.let { PageImageState.Shown(it) } ?: PageImageState.Missing
    }
    PageViewerScreen(page = page, documentName = documentName, image = image, state = state, onClose = onClose)
}

/**
 * F20 (contract U12–U15, wireframe §6 and §10): the page at full screen over the theme's background, with a pinch up
 * to 4×, a double tap between 1× and 2× and a drag once zoomed; TalkBack gets «Ampliar», «Reducir» and «Restablecer».
 * The zoom is not saved: after a rotation it is back at 1×.
 */
@Composable
internal fun PageViewerScreen(page: Int, documentName: String, image: Boolean, state: PageImageState, onClose: () -> Unit) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val title = if (image) documentName else stringResource(R.string.page_viewer_title, documentName, page)
    val description = if (image) stringResource(R.string.image_preview_description, documentName)
    else stringResource(R.string.page_preview_description, page, documentName)
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 64.dp)
                .padding(start = spacing.space1, end = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                LisdataIcon(icon = LisdataIcons.Close, contentDescription = stringResource(R.string.page_viewer_close), tint = colors.textPrimary)
            }
            Text(
                text = title,
                style = LisdataTheme.typography.material.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = spacing.space2).semantics { heading() },
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                PageImageState.Loading ->
                    LisdataProgressRing(progress = ProgressValue.Indeterminate, label = stringResource(R.string.state_loading), size = 72.dp)
                PageImageState.Missing -> Column(
                    modifier = Modifier.padding(spacing.screenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(spacing.space3),
                ) {
                    LisdataInfoCard(icon = LisdataIcons.Info, title = stringResource(R.string.document_citation_page_missing), modifier = Modifier.fillMaxWidth())
                }
                is PageImageState.Shown -> ZoomableImage(state.bitmap, description)
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: ImageBitmap, description: String) {
    val zoom = remember { ZoomState() }
    val zoomIn = stringResource(R.string.page_viewer_zoom_in)
    val zoomOut = stringResource(R.string.page_viewer_zoom_out)
    val reset = stringResource(R.string.page_viewer_zoom_reset)
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                // The image fitted in the viewport, at 1×, as ContentScale.Fit lays it out.
                val fit = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
                zoom.setSizes(size.width.toFloat(), size.height.toFloat(), bitmap.width * fit, bitmap.height * fit)
            }
            .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.doubleTap() }) }
            .pointerInput(zoom) { detectTransformGestures { _, pan, change, _ -> zoom.transform(change, pan.x, pan.y) } }
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offsetX
                translationY = zoom.offsetY
            }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { zoom.zoomIn() },
                    CustomAccessibilityAction(zoomOut) { zoom.zoomOut() },
                    CustomAccessibilityAction(reset) {
                        zoom.reset()
                        true
                    },
                )
            },
    )
}

package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews

@LisdataPreviews
@Composable
private fun PendingDestinationPreview() = LisdataPreviewSurface {
    PendingDestination("document", "F08", args = mapOf("documentId" to 42L, "chunkId" to null), onBack = {})
}

package com.lisdatasolutions.microrag.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Lists loaded in blocks (F08 research R2; F13 research R10): asks for the next block when the end of the
 * [loaded] rows comes into view and [hasMore] says there are more.
 */
@Composable
fun LoadMoreNearTheEnd(listState: LazyListState, loaded: Int, hasMore: Boolean, onLoadMore: () -> Unit) {
    val nearTheEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= layout.totalItemsCount - LOAD_MORE_DISTANCE
        }
    }
    LaunchedEffect(nearTheEnd, loaded, hasMore) {
        if (nearTheEnd && hasMore) onLoadMore()
    }
}

/** Rows from the end at which the next block is asked for: it arrives before the user gets there. */
private const val LOAD_MORE_DISTANCE = 10

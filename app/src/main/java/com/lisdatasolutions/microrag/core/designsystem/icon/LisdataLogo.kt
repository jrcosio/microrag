package com.lisdatasolutions.microrag.core.designsystem.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R

/** Official LISDATA logo asset, scaled by height with its original proportions (FR-007). */
@Composable
fun LisdataLogo(
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    val painter = painterResource(R.drawable.logo_lisdata)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    Image(
        painter = painter,
        contentDescription = stringResource(R.string.cd_logo),
        modifier = modifier
            .height(height)
            .aspectRatio(ratio),
        contentScale = ContentScale.Fit,
    )
}

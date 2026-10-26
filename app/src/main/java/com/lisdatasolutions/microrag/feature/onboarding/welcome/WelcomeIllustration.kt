package com.lisdatasolutions.microrag.feature.onboarding.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataIconTile
import com.lisdatasolutions.microrag.core.designsystem.model.IconTileStyle
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative artwork of PNG 01: orbits around a document with the AI sparkle and the privacy
 * shield. Built from tokens and Lucide icons only; hidden from accessibility services.
 */
@Composable
fun WelcomeIllustration(size: Dp, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val unit = size / 300
    Box(modifier = modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val stroke = Stroke(width = 1.dp.toPx())
            listOf(0.5f, 0.4f, 0.3f).forEach { ratio ->
                drawCircle(color = colors.onDarkTrack, radius = this.size.minDimension * ratio, center = center, style = stroke)
            }
            // Dots riding the orbits, as in the mockup.
            listOf(Triple(0.5f, 200f, colors.brandCyan), Triple(0.4f, 340f, colors.ice200), Triple(0.5f, 70f, colors.brandCyan))
                .forEach { (ratio, degrees, color) ->
                    val radius = this.size.minDimension * ratio
                    val angle = Math.toRadians(degrees.toDouble())
                    val dot = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                    drawCircle(color = color, radius = 6.dp.toPx() * (unit.value), center = dot)
                }
        }
        DocumentCard(width = unit * 120, height = unit * 140)
        LisdataIconTile(
            icon = R.drawable.ic_sparkle,
            style = IconTileStyle.Accent,
            size = unit * 54,
            modifier = Modifier.align(Alignment.Center).offset(x = unit * 62, y = unit * -62),
        )
        LisdataIconTile(
            icon = R.drawable.ic_shield_check,
            style = IconTileStyle.Light,
            size = unit * 58,
            modifier = Modifier.align(Alignment.Center).offset(x = unit * -78, y = unit * 76),
        )
    }
}

@Composable
private fun DocumentCard(width: Dp, height: Dp) {
    val colors = LisdataTheme.colors
    Column(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(width / 8))
            .background(Brush.verticalGradient(listOf(colors.onDarkText, colors.ice100)))
            .padding(width / 7),
    ) {
        LisdataIconTile(icon = R.drawable.ic_file_text, style = IconTileStyle.Light, size = width / 2.6f)
        listOf(1f, 0.75f, 0.85f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(top = height / 14)
                    .fillMaxWidth(fraction)
                    .height(height / 28)
                    .clip(RoundedCornerShape(50))
                    .background(colors.ice200),
            )
        }
    }
}

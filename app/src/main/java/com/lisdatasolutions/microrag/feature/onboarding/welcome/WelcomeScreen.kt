package com.lisdatasolutions.microrag.feature.onboarding.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEyebrow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataStatusChip
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataLogo
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.onboarding.StartOnboardingUseCase
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** P01 Bienvenida (PNG 01, contracts/onboarding-ui.md). Shown only until onboarding starts. */
@Composable
fun WelcomeRoute(onStart: () -> Unit, viewModel: WelcomeViewModel = koinViewModel()) {
    WelcomeScreen(
        onStart = {
            viewModel.onStart()
            onStart()
        },
    )
}

class WelcomeViewModel(private val startOnboarding: StartOnboardingUseCase) : ViewModel() {
    fun onStart() {
        viewModelScope.launch { startOnboarding() }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    val typography = LisdataTheme.typography.material
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LisdataTheme.gradients.welcome),
    ) {
        val illustration = illustrationSize(maxWidth, maxHeight, LocalDensity.current.fontScale)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .safeDrawingPadding()
                .padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space5),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space6)) {
                LisdataLogo(height = 40.dp)
                LisdataStatusChip(text = stringResource(R.string.welcome_chip), kind = StatusKind.Local, onDark = true)
            }
            if (illustration > 0.dp) {
                WelcomeIllustration(size = illustration, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Column {
                Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                    Text(stringResource(R.string.welcome_title_line1), style = typography.displayMedium, color = colors.onDarkText)
                    Text(stringResource(R.string.welcome_title_line2), style = typography.displayMedium, color = colors.onDarkAccent)
                }
                Spacer(Modifier.height(spacing.space5))
                Text(stringResource(R.string.welcome_body_line1), style = typography.bodyLarge, color = colors.onDarkText)
                Text(stringResource(R.string.welcome_body_line2), style = typography.bodyLarge, color = colors.onDarkText.copy(alpha = 0.85f))
                Spacer(Modifier.height(spacing.space7))
                LisdataGradientButton(
                    text = stringResource(R.string.welcome_cta),
                    onClick = onStart,
                    trailingIcon = R.drawable.ic_chevron_right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.space4))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LisdataEyebrow(text = stringResource(R.string.welcome_footer), color = colors.onDarkAccent)
                }
            }
        }
    }
}

/**
 * Compact screens give room to the text and the CTA (MD 01): half size below 640 dp of height or
 * with a large font scale, hidden below 560 dp.
 */
internal fun illustrationSize(maxWidth: Dp, maxHeight: Dp, fontScale: Float): Dp {
    val full = min(maxWidth - 40.dp, 300.dp)
    return when {
        maxHeight < 560.dp -> 0.dp
        maxHeight < 640.dp || fontScale >= 1.5f -> full / 2
        else -> full
    }
}

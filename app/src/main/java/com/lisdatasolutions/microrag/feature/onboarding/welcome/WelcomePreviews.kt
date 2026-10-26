package com.lisdatasolutions.microrag.feature.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme

@Preview(name = "Bienvenida", widthDp = 390, heightDp = 844)
@Composable
private fun WelcomePreview() = LisdataTheme { WelcomeScreen(onStart = {}) }

@Preview(name = "Bienvenida · oscuro", widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeDarkPreview() = LisdataTheme(darkTheme = true) { WelcomeScreen(onStart = {}) }

@Preview(name = "Bienvenida · compacta", widthDp = 360, heightDp = 600)
@Composable
private fun WelcomeCompactPreview() = LisdataTheme { WelcomeScreen(onStart = {}) }

@Preview(name = "Bienvenida · fuente 200 %", widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable
private fun WelcomeLargeFontPreview() = LisdataTheme { WelcomeScreen(onStart = {}) }

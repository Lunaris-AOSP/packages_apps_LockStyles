/*
 * SPDX-FileCopyrightText: 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val ExpressiveTypography = Typography().let { type ->
    Typography(
        displaySmall = type.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = type.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = type.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = type.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = type.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockStylesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
    ) {
        AppWidgetStateProvider(content)
    }
}

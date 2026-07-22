package com.radium.skylark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = SkyBlueOnPrimary,
    primaryContainer = SkyBluePrimaryContainer,
    onPrimaryContainer = SkyBlueOnPrimaryContainer,
    secondary = SkySecondary,
    onSecondary = SkyOnSecondary,
    secondaryContainer = SkySecondaryContainer,
    onSecondaryContainer = SkyOnSecondaryContainer,
    tertiary = SkyTertiary,
    onTertiary = SkyOnTertiary,
    background = SkyBackgroundLight,
    onBackground = SkyOnBackgroundLight,
    surface = SkySurfaceLight,
    onSurface = SkyOnSurfaceLight,
    surfaceVariant = SkySurfaceVariantLight,
    onSurfaceVariant = SkyOnSurfaceVariantLight,
    outline = SkyOutlineLight,
    error = SkyError,
)

private val DarkColors = darkColorScheme(
    primary = SkyBluePrimaryDark,
    onPrimary = SkyBlueOnPrimaryDark,
    primaryContainer = SkyBluePrimaryContainerDark,
    onPrimaryContainer = SkyBlueOnPrimaryContainerDark,
    secondary = SkySecondaryDark,
    onSecondary = SkyOnSecondaryDark,
    secondaryContainer = SkySecondaryContainerDark,
    onSecondaryContainer = SkyOnSecondaryContainerDark,
    tertiary = SkyTertiaryDark,
    onTertiary = SkyOnTertiaryDark,
    background = SkyBackgroundDark,
    onBackground = SkyOnBackgroundDark,
    surface = SkySurfaceDark,
    onSurface = SkyOnSurfaceDark,
    surfaceVariant = SkySurfaceVariantDark,
    onSurfaceVariant = SkyOnSurfaceVariantDark,
    outline = SkyOutlineDark,
    error = SkyErrorDark,
)

/**
 * Skylark 应用主题。
 *
 * 采用固定品牌配色，**不启用动态取色 (Dynamic Color)**，
 * 以保证跨设备视觉一致。仅根据系统深/浅色切换配色方案。
 */
@Composable
fun SkylarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SkylarkTypography,
        content = content,
    )
}

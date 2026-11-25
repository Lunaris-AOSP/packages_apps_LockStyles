/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lunaris.clockmaker.ClockGlassStyle
import org.lunaris.lockstyles.R
import java.util.Locale

/** Shared material controls for clocks, clock-maker text layers and widget surfaces. */
@Composable
internal fun ClockGlassControls(style: ClockGlassStyle, date: Boolean = false, surface: Boolean = false,
    defaultStyle: ClockGlassStyle? = null,
    onChange: (ClockGlassStyle) -> Unit) {
    val s = style.normalized()
    val d = defaultStyle ?: if (date) ClockGlassStyle.DATE_DEFAULT else ClockGlassStyle()
    if (!surface) {
        Text(stringResource(if (date) R.string.clock_glass_date_material else R.string.clock_glass_material),
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text(stringResource(R.string.clock_glass_tint_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
    }
    GlassSlider(R.string.clock_glass_blur_radius, s.blurRadius, 0..120, d.blurRadius, unit = " px") {
        onChange(s.copy(blurRadius = it))
    }
    if (!surface) {
        TuningSlider(stringResource(R.string.clock_glass_glyph_blur), s.glyphBlur, 0..1200, " px", d.glyphBlur,
            displayValue = String.format(Locale.getDefault(), "%.1f px", s.glyphBlur / 10f)) { onChange(s.copy(glyphBlur = it)) }
    }
    GlassSlider(R.string.clock_glass_tint_opacity, s.tintOpacity, 0..100, d.tintOpacity) { onChange(s.copy(tintOpacity = it)) }
    GlassSlider(R.string.clock_glass_contrast, s.contrast, 100..200, d.contrast) { onChange(s.copy(contrast = it)) }
    GlassSlider(R.string.clock_glass_rim_strength, s.rimStrength, if (date) 0..400 else 0..500, d.rimStrength) { onChange(s.copy(rimStrength = it)) }
    GlassSlider(R.string.clock_glass_rim_rotation, s.rimRotation, 0..360, d.rimRotation, unit = "°") { onChange(s.copy(rimRotation = it)) }
    GlassSlider(R.string.clock_glass_soft_rim_strength, s.softRimStrength, 0..300, d.softRimStrength) { onChange(s.copy(softRimStrength = it)) }
    GlassSlider(R.string.clock_glass_soft_rim_width, s.softRimWidth, 25..300, d.softRimWidth) { onChange(s.copy(softRimWidth = it)) }
    GlassSlider(R.string.clock_glass_soft_rim_tint, s.softRimTint, 0..100, d.softRimTint) { onChange(s.copy(softRimTint = it)) }
    GlassSlider(R.string.clock_glass_refraction, s.refraction, if (date) 0..200 else 0..400, d.refraction, optical = true) { onChange(s.copy(refraction = it)) }
    GlassSlider(R.string.clock_glass_refraction_width, s.refractionWidth, if (date || surface) 10..200 else 20..200, d.refractionWidth, optical = true) { onChange(s.copy(refractionWidth = it)) }
    if (!date) GlassSlider(R.string.clock_glass_chromatic, s.chromaticAberration, 0..50, d.chromaticAberration, optical = true) { onChange(s.copy(chromaticAberration = it)) }
    GlassSlider(R.string.clock_glass_shadow, s.shadowStrength, 0..200, d.shadowStrength) { onChange(s.copy(shadowStrength = it)) }
    GlassSlider(R.string.clock_glass_rim_width, s.rimWidth, if (date) -100..100 else 25..300, d.rimWidth) { onChange(s.copy(rimWidth = it)) }
    if (!date) TuningSwitch(stringResource(R.string.clock_glass_black_border), s.blackBorder) { onChange(s.copy(blackBorder = it)) }
    if (!date && s.blackBorder) {
        GlassSlider(R.string.clock_glass_black_border_strength, s.blackBorderStrength, 0..100, d.blackBorderStrength) {
            onChange(s.copy(blackBorderStrength = it))
        }
    }
    if (!surface) {
        TuningSwitch(stringResource(if (date) R.string.clock_glass_separate_date_refraction else R.string.clock_glass_separate_clock),
            s.separateRefraction) { onChange(s.copy(separateRefraction = it)) }
        if (s.separateRefraction) {
            GlassSlider(if (date) R.string.clock_glass_date_refraction else R.string.clock_glass_clock_refraction,
                s.clockRefraction, 0..200, d.clockRefraction, optical = true) { onChange(s.copy(clockRefraction = it)) }
            GlassSlider(if (date) R.string.clock_glass_date_refraction_width else R.string.clock_glass_clock_refraction_width,
                s.clockRefractionWidth, 10..200, d.clockRefractionWidth, optical = true) { onChange(s.copy(clockRefractionWidth = it)) }
            GlassSlider(if (date) R.string.clock_glass_date_rim else R.string.clock_glass_clock_rim,
                s.clockRimStrength, 0..400, d.clockRimStrength) { onChange(s.copy(clockRimStrength = it)) }
        }
    }
    ExpressiveButton(
        label = stringResource(R.string.clock_glass_reset),
        onClick = { onChange(d) },
        primary = false,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
}

@Composable
private fun GlassSlider(label: Int, value: Int, range: IntRange, default: Int,
    unit: String = "%", optical: Boolean = false, onChange: (Int) -> Unit) {
    TuningSlider(stringResource(label), value, range, unit, default,
        displayValue = if (optical) String.format(Locale.getDefault(), "%.1f%%", value / 10f) else null,
        onChange = onChange)
}

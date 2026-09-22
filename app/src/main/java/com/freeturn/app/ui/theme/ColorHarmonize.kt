package com.freeturn.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

internal fun Color.harmonizeWith(source: Color, maxShiftDegrees: Float = 15f): Color {
    val (l, a, b) = toOklab()
    val chroma = hypot(a, b)
    if (chroma < NEUTRAL_CHROMA) return this

    val (_, sourceA, sourceB) = source.toOklab()
    if (hypot(sourceA, sourceB) < NEUTRAL_CHROMA) return this

    val hue = atan2(b, a)
    val maxShift = maxShiftDegrees * DEG_TO_RAD
    val shift = shortestAngle(atan2(sourceB, sourceA) - hue).coerceIn(-maxShift, maxShift)
    val shifted = hue + shift

    return oklabToColor(l, chroma * cos(shifted), chroma * sin(shifted), alpha)
}

private const val NEUTRAL_CHROMA = 1e-4f
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
private const val TWO_PI = (2.0 * Math.PI).toFloat()

/** Разница тонов по короткой дуге: 350° -> 10° это +20°, а не -340°. */
private fun shortestAngle(radians: Float): Float {
    var d = radians % TWO_PI
    if (d > TWO_PI / 2f) d -= TWO_PI
    if (d < -TWO_PI / 2f) d += TWO_PI
    return d
}

private fun Color.toOklab(): Triple<Float, Float, Float> {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)

    val lCone = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val mCone = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val sCone = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    return Triple(
        0.2104542553f * lCone + 0.7936177850f * mCone - 0.0040720468f * sCone,
        1.9779984951f * lCone - 2.4285922050f * mCone + 0.4505937099f * sCone,
        0.0259040371f * lCone + 0.7827717662f * mCone - 0.8086757660f * sCone
    )
}

private fun oklabToColor(l: Float, a: Float, b: Float, alpha: Float): Color {
    val lCone = (l + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val mCone = (l - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val sCone = (l - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }

    // Сдвинутый тон может выпасть за sRGB - обрезаем по каналам.
    return Color(
        red = linearToSrgb(4.0767416621f * lCone - 3.3077115913f * mCone + 0.2309699292f * sCone),
        green = linearToSrgb(-1.2684380046f * lCone + 2.6097574011f * mCone - 0.3413193965f * sCone),
        blue = linearToSrgb(-0.0041960863f * lCone - 0.7034186147f * mCone + 1.7076147010f * sCone),
        alpha = alpha
    )
}

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float {
    // Обрезаем до гаммы: pow от отрицательного - NaN.
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

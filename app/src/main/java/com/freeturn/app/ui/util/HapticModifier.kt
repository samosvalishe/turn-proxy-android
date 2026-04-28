package com.freeturn.app.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import com.freeturn.app.ui.HapticUtil

/**
 * Кликабельность с тактильным откликом. Унифицирует haptic feedback для всех
 * интерактивных элементов вне Material-компонентов (которые могут иметь свой).
 */
fun Modifier.hapticClickable(
    pattern: HapticUtil.Pattern = HapticUtil.Pattern.CLICK,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    clickable(enabled = enabled) {
        HapticUtil.perform(context, pattern)
        onClick()
    }
}

@Composable
fun rememberHaptic(): (HapticUtil.Pattern) -> Unit {
    val context = LocalContext.current
    return { pattern -> HapticUtil.perform(context, pattern) }
}

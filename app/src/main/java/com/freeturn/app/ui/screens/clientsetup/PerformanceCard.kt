package com.freeturn.app.ui.screens.clientsetup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.ui.components.SectionLabel
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsControlLabel
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.ui.components.SettingsRowDivider
import kotlin.math.roundToInt

/** Производительность: потоки и потоки-на-аккаунт. */
@Composable
internal fun PerformanceCard(
    threads: Float,
    onThreads: (Float) -> Unit,
    streamsPerCred: Float,
    onStreamsPerCred: (Float) -> Unit,
    onTick: () -> Unit
) {
    SectionLabel(stringResource(R.string.client_section_performance))
    SettingsCard {
        SettingsFieldSlot {
            SliderRow(
                valueLabel = stringResource(R.string.threads_format, threads.roundToInt()),
                hint = stringResource(R.string.threads_recommendation),
                value = threads,
                valueRange = 1f..128f,
                onValueChange = onThreads,
                onTick = onTick
            )
        }
        SettingsRowDivider()
        SettingsFieldSlot {
            SliderRow(
                valueLabel = stringResource(R.string.streams_per_cred_format, streamsPerCred.roundToInt()),
                hint = stringResource(R.string.streams_per_cred_recommendation),
                value = streamsPerCred,
                valueRange = 1f..50f,
                onValueChange = onStreamsPerCred,
                onTick = onTick
            )
        }
    }
}

/**
 * Ползунок с подписью-значением и пояснением. onTick щёлкает на каждое целочисленное
 * деление (а не на каждый float-кадр) — состояние держится локально внутри строки.
 */
@Composable
private fun SliderRow(
    valueLabel: String,
    hint: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onTick: () -> Unit
) {
    var lastInt by remember { mutableIntStateOf(value.roundToInt()) }
    SettingsControlLabel(title = valueLabel, desc = hint)
    Slider(
        value = value,
        onValueChange = {
            val newInt = it.roundToInt()
            if (newInt != lastInt) {
                onTick()
                lastInt = newInt
            }
            onValueChange(it)
        },
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth()
    )
}

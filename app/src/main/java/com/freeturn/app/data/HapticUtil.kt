package com.freeturn.app.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

object HapticUtil {

    enum class Pattern {
        SELECTION,
        CLICK,
        TOGGLE_ON,
        TOGGLE_OFF,
        SUCCESS,
        ERROR,
        LAUNCH,
    }

    @Suppress("DEPRECATION")
    fun perform(context: Context, pattern: Pattern) {
        // Прямой Vibrator игнорирует системный "Виброотклик при касании" -
        // проверяем настройку сами (performHapticFeedback здесь не подходит: нет View).
        val hapticsEnabled = try {
            Settings.System.getInt(
                context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
            ) == 1
        } catch (_: Exception) { true }
        if (!hapticsEnabled) return
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                Pattern.SELECTION -> VibrationEffect.createOneShot(20, 120)

                Pattern.CLICK -> VibrationEffect.createOneShot(25, 160)

                Pattern.TOGGLE_ON -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 35L, 50L, 20L),
                    intArrayOf(0, 200, 0, 110),
                    -1
                )

                Pattern.TOGGLE_OFF -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 28L, 45L, 16L),
                    intArrayOf(0, 140, 0, 70),
                    -1
                )

                Pattern.SUCCESS -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 22L, 40L, 16L, 40L, 12L),
                    intArrayOf(0, 140, 0, 100, 0, 65),
                    -1
                )

                Pattern.ERROR -> VibrationEffect.createWaveform(
                    longArrayOf(0L, 45L, 25L, 45L, 25L, 30L),
                    intArrayOf(0, 210, 0, 175, 0, 130),
                    -1
                )

                Pattern.LAUNCH -> VibrationEffect.createWaveform(
                    longArrayOf(
                        0L,
                        18L, 55L,
                        18L, 45L,
                        22L, 35L,
                        30L, 80L,
                        15L, 40L,
                        10L
                    ),
                    intArrayOf(
                        0,
                        80, 0,
                        110, 0,
                        150, 0,
                        210, 0,
                        60, 0,
                        30
                    ),
                    -1
                )
            }
            try { vibrator.vibrate(effect) } catch (_: Exception) {}
        } else {
            val duration = when (pattern) {
                Pattern.SELECTION -> 20L
                Pattern.CLICK -> 25L
                Pattern.TOGGLE_ON, Pattern.TOGGLE_OFF -> 50L
                Pattern.SUCCESS -> 60L
                Pattern.ERROR -> 100L
                Pattern.LAUNCH -> 70L
            }
            try { vibrator.vibrate(duration) } catch (_: Exception) {}
        }
    }
}

package com.freeturn.app.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Кладёт текст в системный буфер. [sensitive] — для секретов (ключи, ссылки
 * с WG-конфигом): на Android 13+ скрывает содержимое в превью буфера.
 */
fun Context.copyToClipboard(label: String, text: String, sensitive: Boolean = false) {
    val cm = getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
}

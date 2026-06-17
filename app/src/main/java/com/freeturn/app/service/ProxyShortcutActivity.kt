package com.freeturn.app.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Прозрачный трамплин для App Shortcuts (long-press иконки). Launcher запускает
 * только activity - шлём START/STOP в [ProxyReceiver] и сразу закрываемся.
 */
class ProxyShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ProxyActions.START, ProxyActions.STOP -> sendBroadcast(
                Intent(this, ProxyReceiver::class.java).setAction(intent.action)
            )
        }
        finish()
    }
}

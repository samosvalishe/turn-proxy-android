package com.freeturn.app

import android.app.Application
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Security.getProvider("EdDSA") == null) {
            Security.addProvider(EdDSASecurityProvider())
        }
    }
}

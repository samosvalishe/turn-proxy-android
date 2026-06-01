package com.freeturn.app.di

import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.AppUpdater
import com.freeturn.app.domain.LocalProxyManager
import com.freeturn.app.domain.ProxyOrchestrator
import com.freeturn.app.domain.SshRepository
import com.freeturn.app.viewmodel.ProxyViewModel
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { AppPreferences(androidContext()) }
    single { LocalProxyManager(androidContext()) }
    single { SshRepository(androidContext()) }
    single { AppUpdater(androidContext()) }
    single { ProxyOrchestrator(get(), get(), get()) }

    viewModelOf(::ProxyViewModel)
    viewModelOf(::ServerViewModel)
    viewModelOf(::SettingsViewModel)
}

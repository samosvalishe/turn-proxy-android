package com.freeturn.app.di

import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.AppUpdater
import com.freeturn.app.domain.LinkImportBus
import com.freeturn.app.domain.LocalProxyManager
import com.freeturn.app.domain.ProxyOrchestrator
import com.freeturn.app.domain.ServerSetupRepository
import com.freeturn.app.domain.ShareRepository
import com.freeturn.app.domain.SshRepository
import com.freeturn.app.viewmodel.ImportViewModel
import com.freeturn.app.viewmodel.ProxyViewModel
import com.freeturn.app.viewmodel.ServerSetupViewModel
import com.freeturn.app.viewmodel.ServerViewModel
import com.freeturn.app.viewmodel.SettingsViewModel
import com.freeturn.app.viewmodel.ShareViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { AppPreferences(androidContext()) }
    single { LocalProxyManager(androidContext()) }
    single { SshRepository(androidContext()) }
    single { AppUpdater(androidContext()) }
    single { ProxyOrchestrator(get(), get(), get()) }
    // factory: своя SSH-сессия на каждый прогон мастера, живой SshRepository не трогаем.
    factory { ServerSetupRepository(androidContext()) }
    // factory по той же причине: SSH-операции шаринга не делят сессию с активным сервером.
    factory { ShareRepository(androidContext()) }
    single { LinkImportBus() }

    viewModelOf(::ProxyViewModel)
    viewModelOf(::ServerViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ServerSetupViewModel)
    viewModelOf(::ShareViewModel)
    viewModelOf(::ImportViewModel)
}

package com.freeturn.app.domain.proxy

import kotlinx.coroutines.Job

/**
 * Запуск/остановка платформенного прокси-сервиса. Инверсия зависимости: domain не
 * знает про Android-Service, конкретный Intent держит реализация в слое service.
 */
interface ProxyServiceLauncher {
    fun start(): Job
    fun stop(): Job

    fun restartIfRunning(): Job
}

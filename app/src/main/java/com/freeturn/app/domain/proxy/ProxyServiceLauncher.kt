package com.freeturn.app.domain.proxy

/** Предел перезапусков ядра watchdog'ом перед окончательной остановкой. */
const val MAX_PROXY_RESTARTS = 8

/**
 * Запуск/остановка платформенного прокси-сервиса. Инверсия зависимости: domain не
 * знает про Android-Service, конкретный Intent держит реализация в слое service.
 */
interface ProxyServiceLauncher {
    fun start()
    fun stop()
}

package com.freeturn.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe маршруты навигации. Графы-вкладки нижнего меню — у каждой свой back-stack:
 * бар виден на всех уровнях вложенности, повторный тап по активной вкладке возвращает
 * в её корень. Settings-флоу: Настройки → Серверы → [сервер] → подключение/режим/сервер → SSH.
 */

// --- Графы-вкладки ---
@Serializable data object HomeGraph
@Serializable data object ShareGraph
@Serializable data object AddGraph
@Serializable data object SettingsGraph

// --- Вкладка «Главная» ---
@Serializable data object Home
@Serializable data object Logs

// --- Вкладка «Поделиться» ---
@Serializable data object Share

// --- Вкладка «+» ---
@Serializable data object AddServer
@Serializable data object SelfHostedSetup
@Serializable data object QrScanner

// --- Вкладка «Настройки» ---
@Serializable data object Settings
@Serializable data object AppSettings
@Serializable data object About
@Serializable data object Advanced
@Serializable data object ServersList
@Serializable data object SshSetup
@Serializable data class ServerDetail(val serverId: String)
@Serializable data class ConnectionMode(val serverId: String)
@Serializable data class ClientSetup(val serverId: String)
@Serializable data class ServerManagement(val serverId: String)
@Serializable data class NerdInfo(val serverId: String)

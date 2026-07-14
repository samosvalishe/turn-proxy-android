package com.freeturn.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeGraph
@Serializable data object LogsGraph
@Serializable data object ShareGraph
@Serializable data object AddGraph
@Serializable data object SettingsGraph

@Serializable data object Home

@Serializable data object Logs

@Serializable data object Share

@Serializable data object AddServer
@Serializable data object SelfHostedSetup
@Serializable data object QrScanner

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

package com.freeturn.app.data.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Типизированные data-пейлоады команд (поле `data` в [ControlResponse]).
 * Контракт с install.sh (proto 3): имена полей = ключи JSON (см. cmd_* в скрипте).
 */

@Serializable
data class ProbeData(
    val installed: Boolean = false,
    val running: Boolean = false,
    val euid: Int = -1,
    val arch: String = "",
    val method: String? = null,
    val version: String? = null,
    /** Нет install.conf - сервер не ставился этим установщиком (или старый, до proto 3). */
    val config: RemoteConfig? = null,
)

@Serializable
data class RemoteConfig(
    val method: String = "",
    val backend: String = "",
    val connect: String = "",
    val host: String = "",
    @SerialName("listen_port") val listenPort: Int = 0,
    @SerialName("wg_port") val wgPort: Int = 0,
    @SerialName("wg_net") val wgNet: String = "",
    val mode: String = "",
    @SerialName("obf_profile") val obfProfile: String = "",
    @SerialName("obf_key") val obfKey: String = "",
    val version: String = "",
)

@Serializable
data class ApplyData(
    val owner: OwnerDto = OwnerDto(),
    /** Ключ после apply: пустой в запросе -> сервер сгенерировал свой. */
    @SerialName("obf_key") val obfKey: String = "",
    @SerialName("needs_restart") val needsRestart: Boolean = false,
)

@Serializable
data class OwnerDto(
    @SerialName("client_id") val clientId: String = "",
    val pub: String = "",
    @SerialName("conf_b64") val confB64: String = "",
)

@Serializable
data class ClientDto(
    val name: String = "",
    @SerialName("client_id") val clientId: String = "",
    val self: Boolean = false,
    /** ip/pub/hs - только при своём WG (backend=new). */
    val ip: String = "",
    val pub: String = "",
    val hs: Long = 0,
)

@Serializable
data class ShareDto(
    val backend: String = "",
    val host: String = "",
    val port: Int = 0,
    val mode: String = "",
    @SerialName("obf_profile") val obfProfile: String = "",
    @SerialName("obf_key") val obfKey: String = "",
)

@Serializable
data class ClientListData(
    val clients: List<ClientDto> = emptyList(),
    val share: ShareDto = ShareDto(),
)

@Serializable
data class ClientData(
    val client: ClientDto = ClientDto(),
    @SerialName("conf_b64") val confB64: String? = null,
)

@Serializable
data class LogsData(
    val lines: List<String> = emptyList(),
)

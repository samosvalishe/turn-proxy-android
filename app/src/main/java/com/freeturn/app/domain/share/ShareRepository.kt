package com.freeturn.app.domain.share

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.share.ShareInfo
import com.freeturn.app.data.share.SharedClient
import com.freeturn.app.data.share.SharedClientParser
import com.freeturn.app.data.share.WgPeer
import com.freeturn.app.data.share.WgPeerParser
import com.freeturn.app.domain.server.CmdResult
import com.freeturn.app.domain.server.ServerCommand
import com.freeturn.app.domain.server.ServerCommandException
import com.freeturn.app.domain.server.ServerControl
import com.freeturn.app.domain.server.asUnit
import com.freeturn.app.domain.server.decodeBase64
import com.freeturn.app.domain.server.toFailure
import com.freeturn.app.domain.ssh.SSHManager
import java.util.Base64

/**
 * SSH-операции шаринга доступа (вкладка "Поделиться"). Собственный [SSHManager]
 * и [ServerControl] - как у [ServerSetupRepository]: не трогаем живую сессию
 * активного сервера. Состояния не держит, ошибки - типизированным [Result].
 */
class ShareRepository(context: Context, ssh: SSHManager) {

    private val control = ServerControl(context, ssh)

    /** Свежесозданный пир: клиентский conf для ссылки + pubkey/ip для локального
     *  аппенда в уже загруженный список "Пользователей". */
    data class NewPeer(val pubkey: String, val ip: String, val clientConf: String)

    /** Сохранённый доступ пира для повторной выдачи: conf + его cid из allowlist. */
    data class PeerAccess(val clientConf: String, val clientId: String)

    suspend fun shareInfo(cfg: SshConfig): Result<ShareInfo> =
        when (val r = control.run(cfg, ServerCommand.ShareInfo)) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> Result.success(
                ShareInfo(
                    mode = r.kv["MODE"].orEmpty(),
                    obfProfile = r.kv["OBF_PROFILE"].orEmpty(),
                    obfKey = r.kv["OBF_KEY"].orEmpty(),
                    obfTiming = r.kv["OBF_TIMING"]?.toIntOrNull() ?: 0,
                    wgBackend = r.kv["WG_BACKEND"] == "yes"
                )
            )
        }

    /** [clientId] - свежий cid гостя: скрипт сажает его в allowlist (comment = имя). */
    suspend fun addPeer(
        cfg: SshConfig,
        name: String,
        endpoint: String,
        clientId: String
    ): Result<NewPeer> {
        return when (val r = control.run(cfg, ServerCommand.PeerAdd(nameB64(name), endpoint, clientId))) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> {
                val pubkey = r.kv["PEER_PUB"]
                val conf = r.kv["WG_CLIENT_CONF_B64"]?.let(::decodeBase64)
                if (pubkey.isNullOrBlank() || conf.isNullOrBlank()) {
                    Result.failure(ServerCommandException("server returned no peer config"))
                } else {
                    Result.success(NewPeer(pubkey, r.kv["PEER_IP"].orEmpty(), conf))
                }
            }
        }
    }

    /** WG-пиры + allowlist-гости одной SSH-сессией (вкладка "Пользователи"). */
    suspend fun listShared(cfg: SshConfig): Result<Pair<List<WgPeer>, List<SharedClient>>> =
        when (val r = control.run(cfg, ServerCommand.ShareList)) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> Result.success(
                WgPeerParser.parse(r.kv) to SharedClientParser.parse(r.kv)
            )
        }

    /**
     * Conf пира + его cid. [candidateClientId]/[name] - backfill: пир без
     * cid-маппинга (выдан до allowlist) регистрируется этим cid на сервере.
     */
    suspend fun peerConf(
        cfg: SshConfig,
        pubkey: String,
        candidateClientId: String,
        name: String
    ): Result<PeerAccess> =
        when (val r = control.run(cfg, ServerCommand.PeerConf(pubkey, candidateClientId, nameB64(name)))) {
            is CmdResult.Err -> r.toFailure()
            is CmdResult.Ok -> r.kv["WG_CLIENT_CONF_B64"]?.let(::decodeBase64)
                ?.let { Result.success(PeerAccess(it, r.kv["CLIENT_ID"].orEmpty())) }
                ?: Result.failure(ServerCommandException("server returned no peer config"))
        }

    suspend fun removePeer(cfg: SshConfig, pubkey: String): Result<Unit> =
        control.run(cfg, ServerCommand.PeerRemove(pubkey)).asUnit()

    /** Гость без WG-пира (tcp/Xray-бэкенд): только cid в allowlist. */
    suspend fun addClient(cfg: SshConfig, name: String, clientId: String): Result<Unit> =
        control.run(cfg, ServerCommand.ClientAdd(nameB64(name), clientId)).asUnit()

    suspend fun removeClient(cfg: SshConfig, clientId: String): Result<Unit> =
        control.run(cfg, ServerCommand.ClientRemove(clientId)).asUnit()

    // До 64 символов: влезает в b64-лимит скрипта и не раздувает маркер в conf.
    private fun nameB64(name: String): String = Base64.getEncoder()
        .encodeToString(name.trim().take(64).toByteArray(Charsets.UTF_8))
}

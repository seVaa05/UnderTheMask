package com.underthemask.android.core.repository

import com.underthemask.android.core.datastore.SessionManager
import com.underthemask.android.core.model.GameSettings
import com.underthemask.android.core.model.HintType
import com.underthemask.android.core.model.Lobby
import com.underthemask.android.core.model.PlayerSession
import com.underthemask.android.core.network.CreateLobbyRequestDto
import com.underthemask.android.core.network.ErrorKind
import com.underthemask.android.core.network.ErrorMapper
import com.underthemask.android.core.network.HintTypeDto
import com.underthemask.android.core.network.JoinLobbyRequestDto
import com.underthemask.android.core.network.LobbyApiService
import com.underthemask.android.core.network.UpdateSettingsRequestDto
import com.underthemask.android.core.network.apiCall
import com.underthemask.android.core.network.toDomain
import javax.inject.Inject
import javax.inject.Singleton

interface LobbyRepository {
    suspend fun createLobby(name: String, settings: GameSettings): PlayerSession
    suspend fun joinLobby(code: String, name: String): PlayerSession
    suspend fun getLobby(code: String): Lobby
    suspend fun reconnect(): Lobby
    suspend fun updateSettings(code: String, settings: GameSettings): Lobby
    suspend fun leave(code: String)
    suspend fun currentSession(): PlayerSession?
}

@Singleton
class DefaultLobbyRepository @Inject constructor(
    private val api: LobbyApiService,
    private val sessionManager: SessionManager,
    private val errorMapper: ErrorMapper,
) : LobbyRepository {
    override suspend fun createLobby(name: String, settings: GameSettings): PlayerSession =
        errorMapper.apiCall {
            api.createLobby(
                CreateLobbyRequestDto(name.trim(), settings.impostorCount, settings.hintType.toDto()),
            ).toDomain().also { sessionManager.save(it) }
        }

    override suspend fun joinLobby(code: String, name: String): PlayerSession = errorMapper.apiCall {
        api.joinLobby(normalizeCode(code), JoinLobbyRequestDto(name.trim()))
            .toDomain()
            .also { sessionManager.save(it) }
    }

    override suspend fun getLobby(code: String): Lobby = errorMapper.apiCall {
        api.getLobby(normalizeCode(code)).toDomain()
    }

    override suspend fun reconnect(): Lobby {
        val session = sessionManager.awaitSession() ?: error("No active session")
        return try {
            errorMapper.apiCall {
                val refreshed = api.reconnect(session.lobbyCode).toDomain()
                sessionManager.save(refreshed)
                api.getLobby(refreshed.lobbyCode).toDomain()
            }
        } catch (exception: com.underthemask.android.core.network.AppException) {
            if (exception.error.kind == ErrorKind.NOT_FOUND || exception.error.kind == ErrorKind.UNAUTHORIZED) {
                sessionManager.clear()
            }
            throw exception
        }
    }

    override suspend fun updateSettings(code: String, settings: GameSettings): Lobby = errorMapper.apiCall {
        api.updateSettings(
            normalizeCode(code),
            UpdateSettingsRequestDto(settings.impostorCount, settings.hintType.toDto()),
        ).toDomain()
    }

    override suspend fun leave(code: String) {
        try {
            errorMapper.apiCall { api.leaveLobby(normalizeCode(code)) }
            sessionManager.clear()
        } catch (exception: com.underthemask.android.core.network.AppException) {
            if (exception.error.kind == ErrorKind.NOT_FOUND || exception.error.kind == ErrorKind.UNAUTHORIZED) {
                sessionManager.clear()
                return
            }
            throw exception
        }
    }

    override suspend fun currentSession(): PlayerSession? = sessionManager.awaitSession()

    private fun HintType.toDto() = HintTypeDto.valueOf(name)

    companion object {
        fun normalizeCode(code: String): String = code.trim().uppercase()
    }
}

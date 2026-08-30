package com.underthemask.android.feature.lobby

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.underthemask.android.core.model.GameSettings
import com.underthemask.android.core.model.HintType
import com.underthemask.android.core.model.Lobby
import com.underthemask.android.core.model.LobbyStatus
import com.underthemask.android.core.repository.GameRepository
import com.underthemask.android.core.repository.LobbyRepository
import com.underthemask.android.core.ui.AppEffect
import com.underthemask.android.core.ui.userMessage
import com.underthemask.android.core.websocket.ConnectionState
import com.underthemask.android.core.websocket.LobbyRealtimeEvent
import com.underthemask.android.core.websocket.LobbyUpdatesCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LobbyUiState(
    val lobby: Lobby? = null,
    val playerId: String? = null,
    val isLoading: Boolean = true,
    val isActionPending: Boolean = false,
    val errorMessage: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
) {
    val isHost: Boolean get() = lobby?.hostPlayerId != null && lobby.hostPlayerId == playerId
    val canStart: Boolean get() = isHost
        && lobby?.status == LobbyStatus.WAITING
        && (lobby.playerCount >= lobby.minimumPlayers)
        && (lobby.settings.impostorCount < lobby.playerCount)
        && !isActionPending
}

@HiltViewModel
class LobbyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lobbyRepository: LobbyRepository,
    private val gameRepository: GameRepository,
    private val updatesCoordinator: LobbyUpdatesCoordinator,
) : ViewModel() {
    private val code: String = checkNotNull(savedStateHandle["code"])
    private val _state = MutableStateFlow(LobbyUiState())
    private val _effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 2)

    val state: StateFlow<LobbyUiState> = _state.asStateFlow()
    val effects: SharedFlow<AppEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            updatesCoordinator.connectionState.collectLatest { connection ->
                _state.update { it.copy(connectionState = connection) }
            }
        }
        viewModelScope.launch { refreshLobby(silent = false) }
    }

    fun onScreenStarted() {
        viewModelScope.launch {
            updatesCoordinator.start(
                scope = viewModelScope,
                lobbyCode = code,
                onEvent = { event ->
                    when (event) {
                        LobbyRealtimeEvent.LOBBY_UPDATED -> refreshLobby(silent = true)
                        LobbyRealtimeEvent.GAME_UPDATED -> openGameIfAvailable()
                    }
                },
                onPollingFallback = { refreshLobby(silent = true) },
            )
        }
    }

    fun onScreenStopped() {
        viewModelScope.launch { updatesCoordinator.stop() }
    }

    fun updateImpostorCount(value: Int) = updateSettings(impostorCount = value)
    fun updateHintType(value: HintType) = updateSettings(hintType = value)

    fun startGame() {
        if (!_state.value.canStart) return
        launchAction {
            gameRepository.start(code)
            _effects.emit(AppEffect.OpenGame(code.uppercase()))
        }
    }

    fun leaveLobby() = launchAction {
        lobbyRepository.leave(code)
        updatesCoordinator.stop()
        _effects.emit(AppEffect.OpenHome)
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private fun updateSettings(impostorCount: Int? = null, hintType: HintType? = null) {
        val lobby = _state.value.lobby ?: return
        if (!_state.value.isHost || _state.value.isActionPending) return
        launchAction {
            val settings = GameSettings(
                impostorCount = impostorCount ?: lobby.settings.impostorCount,
                hintType = hintType ?: lobby.settings.hintType,
            )
            val updated = lobbyRepository.updateSettings(code, settings)
            _state.update { it.copy(lobby = updated) }
        }
    }

    private suspend fun refreshLobby(silent: Boolean) {
        updatesCoordinator.serializedRefresh {
            if (!silent) _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val session = lobbyRepository.currentSession()
                val lobby = lobbyRepository.getLobby(code)
                session to lobby
            }.onSuccess { (session, lobby) ->
                _state.update {
                    it.copy(
                        lobby = lobby,
                        playerId = session?.playerId,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                if (lobby.status != LobbyStatus.WAITING) {
                    _effects.emit(AppEffect.OpenGame(lobby.lobbyCode))
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
            }
        }
    }

    private suspend fun openGameIfAvailable() {
        runCatching { gameRepository.get(code) }
            .onSuccess { _effects.emit(AppEffect.OpenGame(code.uppercase())) }
    }

    private fun launchAction(block: suspend () -> Unit) {
        if (_state.value.isActionPending) return
        viewModelScope.launch {
            _state.update { it.copy(isActionPending = true, errorMessage = null) }
            runCatching { block() }
                .onFailure { error -> _state.update { it.copy(errorMessage = error.userMessage()) } }
            _state.update { it.copy(isActionPending = false) }
        }
    }
}

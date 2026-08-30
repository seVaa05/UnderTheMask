package com.underthemask.android.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.underthemask.android.core.model.GamePhase
import com.underthemask.android.core.model.GameState
import com.underthemask.android.core.model.InputValidation
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

data class GameUiState(
    val gameState: GameState? = null,
    val lobby: Lobby? = null,
    val playerId: String? = null,
    val roleRevealed: Boolean = false,
    val clueInput: String = "",
    val selectedPlayerIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isActionPending: Boolean = false,
    val errorMessage: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
) {
    val isHost: Boolean get() = lobby?.hostPlayerId != null && lobby.hostPlayerId == playerId
    val isMyTurn: Boolean get() = gameState?.game?.currentPlayerId == playerId
    val canSubmitClue: Boolean get() = isMyTurn
        && !isActionPending
        && InputValidation.clueError(clueInput, gameState?.secretWord) == null
    val canSubmitVote: Boolean get() {
        val game = gameState?.game ?: return false
        return !isActionPending
            && gameState.hasSubmittedVote.not()
            && InputValidation.voteError(selectedPlayerIds.size, game.requiredSuspectCount) == null
    }
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lobbyRepository: LobbyRepository,
    private val gameRepository: GameRepository,
    private val updatesCoordinator: LobbyUpdatesCoordinator,
) : ViewModel() {
    private val code: String = checkNotNull(savedStateHandle["code"])
    private val _state = MutableStateFlow(GameUiState())
    private val _effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 2)
    private var lastRoundId: String? = null

    val state: StateFlow<GameUiState> = _state.asStateFlow()
    val effects: SharedFlow<AppEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            updatesCoordinator.connectionState.collectLatest { connection ->
                _state.update { it.copy(connectionState = connection) }
            }
        }
        viewModelScope.launch { refreshAll(silent = false) }
    }

    fun onScreenStarted() {
        viewModelScope.launch {
            updatesCoordinator.start(
                scope = viewModelScope,
                lobbyCode = code,
                onEvent = { event ->
                    when (event) {
                        LobbyRealtimeEvent.LOBBY_UPDATED -> refreshLobby()
                        LobbyRealtimeEvent.GAME_UPDATED -> refreshGame(silent = true)
                    }
                },
                onPollingFallback = { refreshAll(silent = true) },
            )
        }
    }

    fun onScreenStopped() {
        viewModelScope.launch { updatesCoordinator.stop() }
    }

    fun toggleRoleVisibility() = _state.update { it.copy(roleRevealed = !it.roleRevealed) }
    fun setClueInput(value: String) = _state.update { it.copy(clueInput = value.take(80), errorMessage = null) }

    fun toggleSuspect(playerId: String) {
        val game = _state.value.gameState?.game ?: return
        if (_state.value.gameState?.hasSubmittedVote == true || _state.value.isActionPending) return
        _state.update {
            it.copy(
                selectedPlayerIds = InputValidation.toggleSuspect(
                    selectedIds = it.selectedPlayerIds,
                    playerId = playerId,
                    requiredCount = game.requiredSuspectCount,
                ),
                errorMessage = null,
            )
        }
    }

    fun submitClue() {
        val snapshot = _state.value
        val error = InputValidation.clueError(snapshot.clueInput, snapshot.gameState?.secretWord)
        if (!snapshot.isMyTurn || error != null) {
            _state.update { it.copy(errorMessage = error ?: "Nije tvoj red za trag.") }
            return
        }
        launchAction {
            val updated = gameRepository.submitClue(code, snapshot.clueInput)
            applyGameState(updated)
            _state.update { it.copy(clueInput = "") }
        }
    }

    fun submitVote() {
        val snapshot = _state.value
        val game = snapshot.gameState?.game ?: return
        val error = InputValidation.voteError(snapshot.selectedPlayerIds.size, game.requiredSuspectCount)
        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }
        launchAction {
            applyGameState(gameRepository.submitVote(code, snapshot.selectedPlayerIds.toList()))
        }
    }

    fun resetGame() = launchAction {
        gameRepository.reset(code)
        _effects.emit(AppEffect.OpenLobby(code.uppercase()))
    }

    fun leaveLobby() = launchAction {
        lobbyRepository.leave(code)
        updatesCoordinator.stop()
        _effects.emit(AppEffect.OpenHome)
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private suspend fun refreshAll(silent: Boolean) {
        updatesCoordinator.serializedRefresh {
            if (!silent) _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val session = lobbyRepository.currentSession()
                val lobby = lobbyRepository.getLobby(code)
                if (lobby.status == LobbyStatus.WAITING) return@runCatching Triple(session, lobby, null)
                Triple(session, lobby, gameRepository.get(code))
            }.onSuccess { (session, lobby, game) ->
                _state.update {
                    it.copy(
                        lobby = lobby,
                        playerId = session?.playerId,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                if (lobby.status == LobbyStatus.WAITING || game == null) {
                    _effects.emit(AppEffect.OpenLobby(lobby.lobbyCode))
                } else {
                    applyGameState(game)
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
            }
        }
    }

    private suspend fun refreshGame(silent: Boolean) {
        updatesCoordinator.serializedRefresh {
            if (!silent) _state.update { it.copy(isLoading = true) }
            runCatching { gameRepository.get(code) }
                .onSuccess(::applyGameState)
                .onFailure { error -> _state.update { it.copy(isLoading = false, errorMessage = error.userMessage()) } }
        }
    }

    private suspend fun refreshLobby() {
        updatesCoordinator.serializedRefresh {
            runCatching { lobbyRepository.getLobby(code) }.onSuccess { lobby ->
                _state.update { it.copy(lobby = lobby) }
                if (lobby.status == LobbyStatus.WAITING) {
                    _effects.emit(AppEffect.OpenLobby(lobby.lobbyCode))
                }
            }
        }
    }

    private fun applyGameState(gameState: GameState) {
        val roundChanged = lastRoundId != gameState.game.roundId
        lastRoundId = gameState.game.roundId
        _state.update {
            it.copy(
                gameState = gameState,
                roleRevealed = if (roundChanged) false else it.roleRevealed,
                selectedPlayerIds = if (roundChanged || gameState.game.phase != GamePhase.VOTING) {
                    emptySet()
                } else {
                    it.selectedPlayerIds
                },
                isLoading = false,
                errorMessage = null,
            )
        }
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

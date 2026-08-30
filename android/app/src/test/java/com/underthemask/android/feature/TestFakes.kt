package com.underthemask.android.feature

import com.underthemask.android.core.model.GamePhase
import com.underthemask.android.core.model.GamePlayer
import com.underthemask.android.core.model.GamePublicState
import com.underthemask.android.core.model.GameSettings
import com.underthemask.android.core.model.GameState
import com.underthemask.android.core.model.HintType
import com.underthemask.android.core.model.Lobby
import com.underthemask.android.core.model.LobbyStatus
import com.underthemask.android.core.model.Player
import com.underthemask.android.core.model.PlayerRole
import com.underthemask.android.core.model.PlayerSession
import com.underthemask.android.core.repository.GameRepository
import com.underthemask.android.core.repository.LobbyRepository
import com.underthemask.android.core.websocket.ConnectionState
import com.underthemask.android.core.websocket.LobbyRealtimeClient
import com.underthemask.android.core.websocket.LobbyRealtimeEvent
import com.underthemask.android.core.websocket.LobbyRealtimeSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeLobbyRepository : LobbyRepository {
    var session: PlayerSession? = PlayerSession("ABC234", "p1", "token")
    var lobby: Lobby = sampleLobby()
    var createResult: PlayerSession = session!!
    var reconnectError: Throwable? = null

    override suspend fun createLobby(name: String, settings: GameSettings): PlayerSession = createResult
    override suspend fun joinLobby(code: String, name: String): PlayerSession = session!!
    override suspend fun getLobby(code: String): Lobby = lobby
    override suspend fun reconnect(): Lobby = reconnectError?.let { throw it } ?: lobby
    override suspend fun updateSettings(code: String, settings: GameSettings): Lobby = lobby.copy(settings = settings)
    override suspend fun leave(code: String) { session = null }
    override suspend fun currentSession(): PlayerSession? = session
}

class FakeGameRepository : GameRepository {
    var state: GameState = sampleGameState()
    var lastVote: List<String>? = null

    override suspend fun start(code: String): GameState = state
    override suspend fun get(code: String): GameState = state
    override suspend fun submitClue(code: String, clue: String): GameState = state
    override suspend fun submitVote(code: String, playerIds: List<String>): GameState {
        lastVote = playerIds
        state = state.copy(hasSubmittedVote = true)
        return state
    }
    override suspend fun reset(code: String): Lobby = sampleLobby()
}

class FakeRealtimeClient : LobbyRealtimeClient {
    override suspend fun connect(lobbyCode: String): LobbyRealtimeSession = FakeRealtimeSession()
}

class FakeRealtimeSession : LobbyRealtimeSession {
    private val mutableState = MutableStateFlow(ConnectionState.CONNECTED)
    private val mutableEvents = MutableSharedFlow<LobbyRealtimeEvent>()
    override val connectionState: StateFlow<ConnectionState> = mutableState
    override val events: SharedFlow<LobbyRealtimeEvent> = mutableEvents
    override suspend fun close() { mutableState.value = ConnectionState.DISCONNECTED }
}

fun sampleLobby() = Lobby(
    lobbyCode = "ABC234",
    status = LobbyStatus.IN_GAME,
    hostPlayerId = "p1",
    settings = GameSettings(2, HintType.CATEGORY),
    players = listOf(
        Player("p1", "Mina", true, true),
        Player("p2", "Luka", true, false),
        Player("p3", "Sara", true, false),
        Player("p4", "Ivan", true, false),
    ),
    playerCount = 4,
    minimumPlayers = 3,
    maxPlayers = 12,
)

fun sampleGameState() = GameState(
    game = GamePublicState(
        roundId = "round-1",
        phase = GamePhase.VOTING,
        currentPlayerId = null,
        players = listOf(
            GamePlayer("p1", "Mina", true),
            GamePlayer("p2", "Luka", true),
            GamePlayer("p3", "Sara", true),
            GamePlayer("p4", "Ivan", true),
        ),
        clues = emptyList(),
        votesSubmitted = 0,
        totalPlayers = 4,
        requiredSuspectCount = 2,
        result = null,
    ),
    role = PlayerRole.CREWMATE,
    secretWord = "Pizza",
    hint = "Hrana",
    hasSubmittedVote = false,
)

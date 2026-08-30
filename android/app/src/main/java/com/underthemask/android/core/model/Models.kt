package com.underthemask.android.core.model

data class PlayerSession(
    val lobbyCode: String,
    val playerId: String,
    internal val reconnectToken: String,
)

enum class HintType { CATEGORY, ASSOCIATION }
enum class LobbyStatus { WAITING, IN_GAME, FINISHED, CLOSED }
enum class GamePhase { CLUES, VOTING, FINISHED }
enum class PlayerRole { CREWMATE, IMPOSTOR }
enum class GameWinner { CREWMATES, IMPOSTORS }

data class GameSettings(
    val impostorCount: Int,
    val hintType: HintType,
)

data class Player(
    val playerId: String,
    val playerName: String,
    val connected: Boolean,
    val isHost: Boolean,
)

data class Lobby(
    val lobbyCode: String,
    val status: LobbyStatus,
    val hostPlayerId: String,
    val settings: GameSettings,
    val players: List<Player>,
    val playerCount: Int,
    val minimumPlayers: Int,
    val maxPlayers: Int,
)

data class GamePlayer(
    val playerId: String,
    val playerName: String,
    val connected: Boolean,
)

data class GameClue(
    val playerId: String,
    val playerName: String,
    val clue: String,
)

data class VoteTally(
    val playerId: String,
    val playerName: String,
    val votes: Int,
)

data class GameResult(
    val winner: GameWinner,
    val secretWord: String,
    val impostorPlayerIds: List<String>,
    val mostVotedPlayerIds: List<String>,
    val tie: Boolean,
    val tallies: List<VoteTally>,
)

data class GamePublicState(
    val roundId: String,
    val phase: GamePhase,
    val currentPlayerId: String?,
    val players: List<GamePlayer>,
    val clues: List<GameClue>,
    val votesSubmitted: Int,
    val totalPlayers: Int,
    val requiredSuspectCount: Int,
    val result: GameResult?,
)

data class GameState(
    val game: GamePublicState,
    val role: PlayerRole,
    val secretWord: String?,
    val hint: String,
    val hasSubmittedVote: Boolean,
)

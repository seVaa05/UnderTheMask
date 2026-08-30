package com.underthemask.android.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

private const val LEGACY_BACKEND_MINIMUM_PLAYERS = 3

@Serializable
data class CreateLobbyRequestDto(
    val hostName: String,
    val impostorCount: Int,
    val hintType: HintTypeDto,
)

@Serializable
data class JoinLobbyRequestDto(val playerName: String)

@Serializable
data class UpdateSettingsRequestDto(
    val impostorCount: Int,
    val hintType: HintTypeDto,
)

@Serializable
data class SubmitClueRequestDto(val clue: String)

@Serializable
data class SubmitVoteRequestDto(val suspectedPlayerIds: List<String>)

@Serializable
enum class HintTypeDto { CATEGORY, ASSOCIATION }

@Serializable
enum class LobbyStatusDto { WAITING, IN_GAME, FINISHED, CLOSED }

@Serializable
enum class GamePhaseDto { CLUES, VOTING, FINISHED }

@Serializable
enum class PlayerRoleDto { CREWMATE, IMPOSTOR }

@Serializable
enum class GameWinnerDto { CREWMATES, IMPOSTORS }

@Serializable
data class LobbySessionDto(
    val lobbyCode: String,
    val playerId: String,
    val reconnectToken: String,
)

@Serializable
data class GameSettingsDto(
    val impostorCount: Int,
    val hintType: HintTypeDto,
)

@Serializable
data class PlayerDto(
    val playerId: String,
    val playerName: String,
    val connected: Boolean,
    val host: Boolean,
)

@Serializable
data class LobbyDto(
    val lobbyCode: String,
    val status: LobbyStatusDto,
    val hostPlayerId: String,
    val settings: GameSettingsDto,
    val players: List<PlayerDto>,
    val playerCount: Int,
    // Compatibility fallback for servers deployed before this response field was introduced.
    val minimumPlayers: Int = LEGACY_BACKEND_MINIMUM_PLAYERS,
    val maxPlayers: Int,
)

@Serializable
data class GamePlayerDto(
    val playerId: String,
    val playerName: String,
    val connected: Boolean,
)

@Serializable
data class GameClueDto(
    val playerId: String,
    val playerName: String,
    val clue: String,
)

@Serializable
data class VoteTallyDto(
    val playerId: String,
    val playerName: String,
    val votes: Int,
)

@Serializable
data class GameResultDto(
    val winner: GameWinnerDto,
    val secretWord: String,
    val impostorPlayerIds: List<String>,
    val mostVotedPlayerIds: List<String>,
    val tie: Boolean,
    val tallies: List<VoteTallyDto>,
)

@Serializable
data class GamePublicStateDto(
    val roundId: String,
    val phase: GamePhaseDto,
    val currentPlayerId: String? = null,
    val players: List<GamePlayerDto>,
    val clues: List<GameClueDto>,
    val votesSubmitted: Int,
    val totalPlayers: Int,
    val requiredSuspectCount: Int,
    val result: GameResultDto? = null,
)

@Serializable
data class GameStateDto(
    val game: GamePublicStateDto,
    val role: PlayerRoleDto,
    val secretWord: String? = null,
    val hint: String,
    val hasSubmittedVote: Boolean,
)

@Serializable
data class ApiErrorDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
)

@Serializable
data class RealtimeSignalDto(
    val type: RealtimeEventTypeDto,
    val payload: JsonElement,
    val occurredAt: String,
)

@Serializable
enum class RealtimeEventTypeDto { LOBBY_UPDATED, GAME_UPDATED }

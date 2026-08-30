package com.underthemask.android.core.network

import com.underthemask.android.core.model.GameClue
import com.underthemask.android.core.model.GamePhase
import com.underthemask.android.core.model.GamePlayer
import com.underthemask.android.core.model.GamePublicState
import com.underthemask.android.core.model.GameResult
import com.underthemask.android.core.model.GameSettings
import com.underthemask.android.core.model.GameState
import com.underthemask.android.core.model.GameWinner
import com.underthemask.android.core.model.HintType
import com.underthemask.android.core.model.Lobby
import com.underthemask.android.core.model.LobbyStatus
import com.underthemask.android.core.model.Player
import com.underthemask.android.core.model.PlayerRole
import com.underthemask.android.core.model.PlayerSession
import com.underthemask.android.core.model.VoteTally

fun LobbySessionDto.toDomain() = PlayerSession(lobbyCode, playerId, reconnectToken)

fun LobbyDto.toDomain() = Lobby(
    lobbyCode = lobbyCode,
    status = LobbyStatus.valueOf(status.name),
    hostPlayerId = hostPlayerId,
    settings = GameSettings(settings.impostorCount, HintType.valueOf(settings.hintType.name)),
    players = players.map { Player(it.playerId, it.playerName, it.connected, it.host) },
    playerCount = playerCount,
    minimumPlayers = minimumPlayers,
    maxPlayers = maxPlayers,
)

fun GameStateDto.toDomain() = GameState(
    game = game.toDomain(),
    role = PlayerRole.valueOf(role.name),
    secretWord = secretWord,
    hint = hint,
    hasSubmittedVote = hasSubmittedVote,
)

private fun GamePublicStateDto.toDomain() = GamePublicState(
    roundId = roundId,
    phase = GamePhase.valueOf(phase.name),
    currentPlayerId = currentPlayerId,
    players = players.map { GamePlayer(it.playerId, it.playerName, it.connected) },
    clues = clues.map { GameClue(it.playerId, it.playerName, it.clue) },
    votesSubmitted = votesSubmitted,
    totalPlayers = totalPlayers,
    requiredSuspectCount = requiredSuspectCount,
    result = result?.let {
        GameResult(
            winner = GameWinner.valueOf(it.winner.name),
            secretWord = it.secretWord,
            impostorPlayerIds = it.impostorPlayerIds,
            mostVotedPlayerIds = it.mostVotedPlayerIds,
            tie = it.tie,
            tallies = it.tallies.map { tally -> VoteTally(tally.playerId, tally.playerName, tally.votes) },
        )
    },
)

package com.underthemask.android.core.network

import com.underthemask.android.core.model.GamePhase
import com.underthemask.android.core.model.PlayerRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtoMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `game DTO preserves backend private and public fields`() {
        val dto = json.decodeFromString<GameStateDto>(GAME_JSON)
        val model = dto.toDomain()

        assertEquals(PlayerRole.IMPOSTOR, model.role)
        assertNull(model.secretWord)
        assertEquals("Italija", model.hint)
        assertEquals(GamePhase.CLUES, model.game.phase)
        assertEquals("p2", model.game.currentPlayerId)
        assertEquals("more", model.game.clues.single().clue)
        assertEquals(1, model.game.requiredSuspectCount)
    }

    @Test
    fun `lobby DTO maps host and settings`() {
        val dto = LobbyDto(
            lobbyCode = "ABC234",
            status = LobbyStatusDto.WAITING,
            hostPlayerId = "p1",
            settings = GameSettingsDto(2, HintTypeDto.ASSOCIATION),
            players = listOf(PlayerDto("p1", "Mina", true, true)),
            playerCount = 1,
            minimumPlayers = 3,
            maxPlayers = 12,
        )
        val lobby = dto.toDomain()

        assertEquals(2, lobby.settings.impostorCount)
        assertEquals(3, lobby.minimumPlayers)
        assertEquals("Mina", lobby.players.single().playerName)
        assertEquals(true, lobby.players.single().isHost)
    }

    @Test
    fun `lobby DTO remains compatible when deployed backend omits minimum players`() {
        val dto = json.decodeFromString<LobbyDto>(LOBBY_WITHOUT_MINIMUM_PLAYERS_JSON)

        assertEquals(3, dto.toDomain().minimumPlayers)
    }

    private companion object {
        const val GAME_JSON = """
            {
              "game": {
                "roundId": "round-1",
                "phase": "CLUES",
                "currentPlayerId": "p2",
                "players": [
                  {"playerId":"p1","playerName":"Mina","connected":true},
                  {"playerId":"p2","playerName":"Luka","connected":true}
                ],
                "clues": [{"playerId":"p1","playerName":"Mina","clue":"more"}],
                "votesSubmitted": 0,
                "totalPlayers": 2,
                "requiredSuspectCount": 1,
                "result": null
              },
              "role": "IMPOSTOR",
              "secretWord": null,
              "hint": "Italija",
              "hasSubmittedVote": false
            }
        """

        const val LOBBY_WITHOUT_MINIMUM_PLAYERS_JSON = """
            {
              "lobbyCode": "ABC234",
              "status": "WAITING",
              "hostPlayerId": "p1",
              "settings": {"impostorCount": 1, "hintType": "CATEGORY"},
              "players": [
                {"playerId":"p1","playerName":"Mina","connected":true,"host":true}
              ],
              "playerCount": 1,
              "maxPlayers": 12
            }
        """
    }
}

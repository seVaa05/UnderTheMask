package com.sevaa05.underthemask.lobby.dto;

import com.sevaa05.underthemask.lobby.model.Lobby;
import com.sevaa05.underthemask.lobby.model.LobbyStatus;
import java.util.List;
import java.util.UUID;

public record LobbyResponse(
        String lobbyCode,
        LobbyStatus status,
        UUID hostPlayerId,
        GameSettingsResponse settings,
        List<PlayerResponse> players,
        int playerCount,
        int minimumPlayers,
        int maxPlayers
) {

    public static LobbyResponse from(Lobby lobby) {
        List<PlayerResponse> playerResponses = lobby.getPlayers().stream()
                .map(player -> PlayerResponse.from(player, lobby.getHostPlayerId()))
                .toList();
        return new LobbyResponse(
                lobby.getCode(),
                lobby.getStatus(),
                lobby.getHostPlayerId(),
                GameSettingsResponse.from(lobby.getSettings()),
                playerResponses,
                lobby.getPlayerCount(),
                Lobby.MIN_PLAYERS,
                Lobby.MAX_PLAYERS
        );
    }
}

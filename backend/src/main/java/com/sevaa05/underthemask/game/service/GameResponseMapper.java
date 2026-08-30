package com.sevaa05.underthemask.game.service;

import com.sevaa05.underthemask.game.dto.GameClueResponse;
import com.sevaa05.underthemask.game.dto.GamePlayerResponse;
import com.sevaa05.underthemask.game.dto.GamePublicResponse;
import com.sevaa05.underthemask.game.dto.GameResultResponse;
import com.sevaa05.underthemask.game.dto.GameStateResponse;
import com.sevaa05.underthemask.game.dto.VoteTallyResponse;
import com.sevaa05.underthemask.game.model.GamePhase;
import com.sevaa05.underthemask.game.model.GameRound;
import com.sevaa05.underthemask.game.model.PlayerRole;
import com.sevaa05.underthemask.lobby.model.Lobby;
import com.sevaa05.underthemask.lobby.model.Player;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GameResponseMapper {

    public GameStateResponse toPrivateResponse(Lobby lobby, GameRound gameRound, Player player) {
        boolean impostor = gameRound.isImpostor(player.getId());
        return new GameStateResponse(
                toPublicResponse(lobby, gameRound),
                impostor ? PlayerRole.IMPOSTOR : PlayerRole.CREWMATE,
                impostor ? null : gameRound.getSecretWord(),
                impostor ? gameRound.getImpostorHint() : gameRound.getCategory(),
                gameRound.hasVoted(player.getId())
        );
    }

    public GamePublicResponse toPublicResponse(Lobby lobby, GameRound gameRound) {
        Map<UUID, Player> playersById = lobby.getPlayers().stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        List<GamePlayerResponse> players = gameRound.getTurnOrder().stream()
                .map(playersById::get)
                .map(player -> new GamePlayerResponse(player.getId(), player.getName(), player.isConnected()))
                .toList();
        List<GameClueResponse> clues = gameRound.getTurnOrder().stream()
                .filter(gameRound.getClues()::containsKey)
                .map(playerId -> new GameClueResponse(
                        playerId,
                        playersById.get(playerId).getName(),
                        gameRound.getClues().get(playerId)
                ))
                .toList();

        GameResultResponse result = null;
        if (gameRound.getPhase() == GamePhase.FINISHED) {
            GameRound.VoteOutcome outcome = gameRound.calculateOutcome();
            List<VoteTallyResponse> tallies = gameRound.getTurnOrder().stream()
                    .map(playerId -> new VoteTallyResponse(
                            playerId,
                            playersById.get(playerId).getName(),
                            outcome.tallies().getOrDefault(playerId, 0)
                    ))
                    .toList();
            result = new GameResultResponse(
                    outcome.winner(),
                    gameRound.getSecretWord(),
                    List.copyOf(gameRound.getImpostorPlayerIds()),
                    outcome.mostVotedPlayerIds(),
                    outcome.tie(),
                    tallies
            );
        }

        return new GamePublicResponse(
                gameRound.getId(),
                gameRound.getPhase(),
                gameRound.getCurrentPlayerId().orElse(null),
                players,
                clues,
                gameRound.getVotes().size(),
                gameRound.getTurnOrder().size(),
                lobby.getSettings().getImpostorCount(),
                result
        );
    }
}

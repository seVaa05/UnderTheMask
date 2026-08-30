package com.sevaa05.underthemask.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sevaa05.underthemask.game.dto.GameStateResponse;
import com.sevaa05.underthemask.game.model.GamePhase;
import com.sevaa05.underthemask.game.model.GameWinner;
import com.sevaa05.underthemask.game.model.PlayerRole;
import com.sevaa05.underthemask.game.service.exception.GameStateException;
import com.sevaa05.underthemask.lobby.model.GameSettings;
import com.sevaa05.underthemask.lobby.model.HintType;
import com.sevaa05.underthemask.lobby.model.LobbySession;
import com.sevaa05.underthemask.lobby.model.LobbyStatus;
import com.sevaa05.underthemask.lobby.service.LobbyService;
import com.sevaa05.underthemask.lobby.store.InMemoryLobbyStore;
import com.sevaa05.underthemask.lobby.store.LobbyStore;
import com.sevaa05.underthemask.realtime.service.RealtimeEventPublisher;
import com.sevaa05.underthemask.realtime.service.NoOpRealtimeEventPublisher;
import com.sevaa05.underthemask.word.service.WordContentService;
import com.sevaa05.underthemask.word.service.WordContentService.WordSelection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameServiceTest {

    private LobbyService lobbyService;
    private GameService gameService;
    private LobbySession host;
    private LobbySession secondPlayer;
    private LobbySession thirdPlayer;

    @BeforeEach
    void setUp() {
        LobbyStore lobbyStore = new InMemoryLobbyStore();
        RealtimeEventPublisher noOpPublisher = new NoOpRealtimeEventPublisher();
        WordContentService wordContentService = mock(WordContentService.class);
        when(wordContentService.findRandomPlayableWord(true))
                .thenReturn(Optional.of(new WordSelection("Pizza", "Hrana", "Italija")));

        lobbyService = new LobbyService(lobbyStore, noOpPublisher);
        gameService = new GameService(lobbyStore, wordContentService, noOpPublisher, new GameResponseMapper());
        host = lobbyService.createLobby("Host", new GameSettings(1, HintType.ASSOCIATION));
        secondPlayer = lobbyService.joinLobby(host.lobbyCode(), "Mira");
        thirdPlayer = lobbyService.joinLobby(host.lobbyCode(), "Luka");
    }

    @Test
    void completeRoundAssignsPrivateRolesCollectsCluesAndResolvesVoting() {
        gameService.startGame(host.lobbyCode(), host.reconnectToken());
        List<LobbySession> sessions = List.of(host, secondPlayer, thirdPlayer);
        Map<String, LobbySession> sessionsById = sessions.stream()
                .collect(Collectors.toMap(session -> session.playerId().toString(), Function.identity()));

        List<GameStateResponse> playerStates = sessions.stream()
                .map(session -> gameService.getGame(host.lobbyCode(), session.reconnectToken()))
                .toList();
        assertThat(playerStates).filteredOn(state -> state.role() == PlayerRole.IMPOSTOR).hasSize(1);
        assertThat(playerStates).filteredOn(state -> state.role() == PlayerRole.CREWMATE)
                .allSatisfy(state -> assertThat(state.secretWord()).isEqualTo("Pizza"));
        assertThat(playerStates).filteredOn(state -> state.role() == PlayerRole.IMPOSTOR)
                .allSatisfy(state -> {
                    assertThat(state.secretWord()).isNull();
                    assertThat(state.hint()).isEqualTo("Italija");
                });

        GameStateResponse current = playerStates.get(0);
        int clueNumber = 1;
        while (current.game().phase() == GamePhase.CLUES) {
            LobbySession currentSession = sessionsById.get(current.game().currentPlayerId().toString());
            current = gameService.submitClue(
                    host.lobbyCode(),
                    currentSession.reconnectToken(),
                    "trag" + clueNumber++
            );
        }
        assertThat(current.game().phase()).isEqualTo(GamePhase.VOTING);
        assertThat(current.game().clues()).hasSize(3);

        LobbySession impostorSession = sessions.stream()
                .filter(session -> gameService.getGame(host.lobbyCode(), session.reconnectToken()).role() == PlayerRole.IMPOSTOR)
                .findFirst()
                .orElseThrow();
        String impostorId = impostorSession.playerId().toString();

        for (LobbySession session : sessions) {
            String targetId = session.playerId().toString().equals(impostorId)
                    ? sessions.stream().filter(other -> !other.playerId().equals(session.playerId())).findFirst().orElseThrow().playerId().toString()
                    : impostorId;
            current = gameService.submitVote(
                    host.lobbyCode(),
                    session.reconnectToken(),
                    List.of(java.util.UUID.fromString(targetId))
            );
        }

        assertThat(current.game().phase()).isEqualTo(GamePhase.FINISHED);
        assertThat(current.game().result()).isNotNull();
        assertThat(current.game().result().winner()).isEqualTo(GameWinner.CREWMATES);
        assertThat(current.game().result().secretWord()).isEqualTo("Pizza");
        assertThat(lobbyService.getLobby(host.lobbyCode()).status()).isEqualTo(LobbyStatus.FINISHED);

        gameService.resetGame(host.lobbyCode(), host.reconnectToken());
        assertThat(lobbyService.getLobby(host.lobbyCode()).status()).isEqualTo(LobbyStatus.WAITING);
    }

    @Test
    void nonHostCannotStartGame() {
        assertThatThrownBy(() -> gameService.startGame(host.lobbyCode(), secondPlayer.reconnectToken()))
                .isInstanceOf(GameStateException.class)
                .hasMessage("Only the host can start the game.");
    }
}

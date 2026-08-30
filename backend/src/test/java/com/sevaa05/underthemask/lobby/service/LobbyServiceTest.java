package com.sevaa05.underthemask.lobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sevaa05.underthemask.lobby.dto.LobbyResponse;
import com.sevaa05.underthemask.lobby.model.GameSettings;
import com.sevaa05.underthemask.lobby.model.HintType;
import com.sevaa05.underthemask.lobby.model.Lobby;
import com.sevaa05.underthemask.lobby.model.LobbySession;
import com.sevaa05.underthemask.lobby.service.exception.DuplicatePlayerNameException;
import com.sevaa05.underthemask.lobby.service.exception.InvalidGameSettingsException;
import com.sevaa05.underthemask.lobby.service.exception.LobbyFullException;
import com.sevaa05.underthemask.lobby.service.exception.OnlyHostCanUpdateSettingsException;
import com.sevaa05.underthemask.lobby.service.exception.UnauthorizedPlayerTokenException;
import com.sevaa05.underthemask.lobby.store.InMemoryLobbyStore;
import com.sevaa05.underthemask.lobby.store.LobbyStore;
import com.sevaa05.underthemask.realtime.service.RealtimeEventPublisher;
import com.sevaa05.underthemask.realtime.service.NoOpRealtimeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LobbyServiceTest {

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        LobbyStore lobbyStore = new InMemoryLobbyStore();
        RealtimeEventPublisher noOpPublisher = new NoOpRealtimeEventPublisher();
        lobbyService = new LobbyService(lobbyStore, noOpPublisher);
    }

    @Test
    void hostCreatesLobbyAndBecomesFirstPlayer() {
        LobbySession session = lobbyService.createLobby("Lazar", settings());

        LobbyResponse lobby = lobbyService.getLobby(session.lobbyCode());

        assertThat(lobby.lobbyCode()).isEqualTo(session.lobbyCode());
        assertThat(lobby.playerCount()).isEqualTo(1);
        assertThat(lobby.minimumPlayers()).isEqualTo(Lobby.MIN_PLAYERS);
        assertThat(lobby.maxPlayers()).isEqualTo(Lobby.MAX_PLAYERS);
        assertThat(lobby.hostPlayerId()).isEqualTo(session.playerId());
        assertThat(lobby.players()).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isEqualTo(session.playerId());
            assertThat(player.playerName()).isEqualTo("Lazar");
            assertThat(player.connected()).isTrue();
            assertThat(player.host()).isTrue();
        });
    }

    @Test
    void secondPlayerCanJoin() {
        LobbySession host = lobbyService.createLobby("Lazar", settings());

        LobbySession joined = lobbyService.joinLobby(host.lobbyCode(), "Mira");

        LobbyResponse lobby = lobbyService.getLobby(host.lobbyCode());
        assertThat(joined.playerId()).isNotEqualTo(host.playerId());
        assertThat(lobby.playerCount()).isEqualTo(2);
        assertThat(lobby.players())
                .extracting("playerName")
                .containsExactly("Lazar", "Mira");
    }

    @Test
    void thirteenthPlayerIsRejected() {
        LobbySession host = lobbyService.createLobby("Player1", settings());
        for (int i = 2; i <= Lobby.MAX_PLAYERS; i++) {
            lobbyService.joinLobby(host.lobbyCode(), "Player" + i);
        }

        assertThatThrownBy(() -> lobbyService.joinLobby(host.lobbyCode(), "Player13"))
                .isInstanceOf(LobbyFullException.class);

        assertThat(lobbyService.getLobby(host.lobbyCode()).playerCount()).isEqualTo(Lobby.MAX_PLAYERS);
    }

    @Test
    void duplicateNamesAreRejectedCaseInsensitively() {
        LobbySession host = lobbyService.createLobby("Lazar", settings());

        assertThatThrownBy(() -> lobbyService.joinLobby(host.lobbyCode(), "lAzAr"))
                .isInstanceOf(DuplicatePlayerNameException.class);
    }

    @Test
    void onlyHostCanUpdateSettings() {
        LobbySession host = lobbyService.createLobby("Lazar", settings());
        LobbySession player = lobbyService.joinLobby(host.lobbyCode(), "Mira");

        assertThatThrownBy(() -> lobbyService.updateSettings(
                host.lobbyCode(),
                player.reconnectToken(),
                new GameSettings(2, HintType.CATEGORY)
        )).isInstanceOf(OnlyHostCanUpdateSettingsException.class);

        LobbyResponse updated = lobbyService.updateSettings(
                host.lobbyCode(),
                host.reconnectToken(),
                new GameSettings(2, HintType.CATEGORY)
        );

        assertThat(updated.settings().impostorCount()).isEqualTo(2);
        assertThat(updated.settings().hintType()).isEqualTo(HintType.CATEGORY);
    }

    @Test
    void invalidImpostorCountsAreRejected() {
        assertThatThrownBy(() -> new GameSettings(0, HintType.ASSOCIATION))
                .isInstanceOf(InvalidGameSettingsException.class);
        assertThatThrownBy(() -> new GameSettings(3, HintType.ASSOCIATION))
                .isInstanceOf(InvalidGameSettingsException.class);
    }

    @Test
    void disconnectedPlayerCanReconnectWithValidToken() {
        LobbySession host = lobbyService.createLobby("Lazar", settings());

        lobbyService.disconnectPlayer(host.lobbyCode(), host.reconnectToken());

        assertThat(lobbyService.getLobby(host.lobbyCode()).players())
                .singleElement()
                .satisfies(player -> assertThat(player.connected()).isFalse());

        LobbySession reconnected = lobbyService.reconnect(host.lobbyCode(), host.reconnectToken());

        assertThat(reconnected.playerId()).isEqualTo(host.playerId());
        assertThat(reconnected.reconnectToken()).isEqualTo(host.reconnectToken());
        assertThat(lobbyService.getLobby(host.lobbyCode()).players())
                .singleElement()
                .satisfies(player -> assertThat(player.connected()).isTrue());
    }

    @Test
    void invalidTokenIsRejected() {
        LobbySession host = lobbyService.createLobby("Lazar", settings());

        assertThatThrownBy(() -> lobbyService.reconnect(host.lobbyCode(), "not-a-valid-token"))
                .isInstanceOf(UnauthorizedPlayerTokenException.class);
        assertThatThrownBy(() -> lobbyService.updateSettings(host.lobbyCode(), "not-a-valid-token", settings()))
                .isInstanceOf(UnauthorizedPlayerTokenException.class);
    }

    private GameSettings settings() {
        return new GameSettings(1, HintType.ASSOCIATION);
    }
}

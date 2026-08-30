package com.sevaa05.underthemask.game.service;

import com.sevaa05.underthemask.game.dto.GamePublicResponse;
import com.sevaa05.underthemask.game.dto.GameStateResponse;
import com.sevaa05.underthemask.game.model.GamePhase;
import com.sevaa05.underthemask.game.model.GameRound;
import com.sevaa05.underthemask.game.service.exception.GameStateException;
import com.sevaa05.underthemask.lobby.dto.LobbyResponse;
import com.sevaa05.underthemask.lobby.model.HintType;
import com.sevaa05.underthemask.lobby.model.Lobby;
import com.sevaa05.underthemask.lobby.model.LobbyStatus;
import com.sevaa05.underthemask.lobby.model.Player;
import com.sevaa05.underthemask.lobby.service.exception.InvalidLobbyCodeException;
import com.sevaa05.underthemask.lobby.service.exception.LobbyNotFoundException;
import com.sevaa05.underthemask.lobby.service.exception.UnauthorizedPlayerTokenException;
import com.sevaa05.underthemask.lobby.store.LobbyStore;
import com.sevaa05.underthemask.realtime.service.RealtimeEventPublisher;
import com.sevaa05.underthemask.word.service.WordContentService;
import com.sevaa05.underthemask.word.service.WordContentService.WordSelection;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GameService {

    private static final Pattern LOBBY_CODE_PATTERN = Pattern.compile("^[A-HJ-NP-Z2-9]{6}$");

    private final LobbyStore lobbyStore;
    private final WordContentService wordContentService;
    private final RealtimeEventPublisher eventPublisher;
    private final GameResponseMapper responseMapper;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public GameService(LobbyStore lobbyStore, WordContentService wordContentService,
                       RealtimeEventPublisher eventPublisher, GameResponseMapper responseMapper) {
        this(lobbyStore, wordContentService, eventPublisher, responseMapper, new SecureRandom(), Clock.systemUTC());
    }

    GameService(LobbyStore lobbyStore, WordContentService wordContentService,
                RealtimeEventPublisher eventPublisher, GameResponseMapper responseMapper,
                SecureRandom secureRandom, Clock clock) {
        this.lobbyStore = lobbyStore;
        this.wordContentService = wordContentService;
        this.eventPublisher = eventPublisher;
        this.responseMapper = responseMapper;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public GameStateResponse startGame(String code, String reconnectToken) {
        Lobby lobby = requireLobby(code);
        GameStateResponse response;
        GamePublicResponse eventPayload;
        LobbyResponse lobbyPayload;

        synchronized (lobby) {
            Player host = requirePlayerByToken(lobby, reconnectToken);
            requireHost(lobby, host, "ONLY_HOST_CAN_START_GAME", "Only the host can start the game.");
            if (lobby.getStatus() != LobbyStatus.WAITING && lobby.getStatus() != LobbyStatus.FINISHED) {
                throw GameStateException.conflict("GAME_ALREADY_STARTED", "A game is already in progress.");
            }
            if (lobby.getPlayerCount() < Lobby.MIN_PLAYERS) {
                throw GameStateException.conflict(
                        "NOT_ENOUGH_PLAYERS",
                        "At least " + Lobby.MIN_PLAYERS + " players are required to start."
                );
            }
            if (lobby.getSettings().getImpostorCount() >= lobby.getPlayerCount()) {
                throw GameStateException.conflict(
                        "TOO_MANY_IMPOSTORS",
                        "The impostor count must be lower than the player count."
                );
            }

            boolean associationRequired = lobby.getSettings().getHintType() == HintType.ASSOCIATION;
            WordSelection selection = wordContentService.findRandomPlayableWord(associationRequired)
                    .orElseThrow(() -> GameStateException.unavailable(
                            "GAME_CONTENT_UNAVAILABLE",
                            "No playable words are available. Run the database migrations and try again."
                    ));

            List<UUID> turnOrder = lobby.getPlayers().stream().map(Player::getId).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(turnOrder, secureRandom);
            List<UUID> roleOrder = new ArrayList<>(turnOrder);
            Collections.shuffle(roleOrder, secureRandom);
            Set<UUID> impostorIds = new LinkedHashSet<>(
                    roleOrder.subList(0, lobby.getSettings().getImpostorCount())
            );
            String impostorHint = associationRequired ? selection.association() : selection.category();

            GameRound gameRound = new GameRound(
                    UUID.randomUUID(),
                    selection.word(),
                    selection.category(),
                    impostorHint,
                    impostorIds,
                    turnOrder
            );
            lobby.startGame(gameRound);
            lobby.touch(clock.instant());
            response = responseMapper.toPrivateResponse(lobby, gameRound, host);
            eventPayload = responseMapper.toPublicResponse(lobby, gameRound);
            lobbyPayload = LobbyResponse.from(lobby);
        }

        eventPublisher.publishLobbyUpdated(lobby.getCode(), lobbyPayload);
        eventPublisher.publishGameUpdated(lobby.getCode(), eventPayload);
        return response;
    }

    public GameStateResponse getGame(String code, String reconnectToken) {
        Lobby lobby = requireLobby(code);
        synchronized (lobby) {
            Player player = requirePlayerByToken(lobby, reconnectToken);
            GameRound gameRound = requireGame(lobby);
            return responseMapper.toPrivateResponse(lobby, gameRound, player);
        }
    }

    public GameStateResponse submitClue(String code, String reconnectToken, String rawClue) {
        Lobby lobby = requireLobby(code);
        GameStateResponse response;
        GamePublicResponse eventPayload;

        synchronized (lobby) {
            Player player = requirePlayerByToken(lobby, reconnectToken);
            GameRound gameRound = requireGameInProgress(lobby);
            String clue = rawClue == null ? "" : rawClue.trim();
            if (clue.isBlank()) {
                throw GameStateException.conflict("INVALID_CLUE", "Clue is required.");
            }
            if (clue.equalsIgnoreCase(gameRound.getSecretWord())) {
                throw GameStateException.conflict("SECRET_WORD_AS_CLUE", "The secret word cannot be used as a clue.");
            }
            UUID currentPlayerId = gameRound.getCurrentPlayerId().orElse(null);
            if (!player.getId().equals(currentPlayerId)) {
                throw GameStateException.conflict("NOT_YOUR_TURN", "It is not your turn to submit a clue.");
            }

            gameRound.submitClue(player.getId(), clue);
            lobby.touch(clock.instant());
            response = responseMapper.toPrivateResponse(lobby, gameRound, player);
            eventPayload = responseMapper.toPublicResponse(lobby, gameRound);
        }

        eventPublisher.publishGameUpdated(lobby.getCode(), eventPayload);
        return response;
    }

    public GameStateResponse submitVote(String code, String reconnectToken, List<UUID> suspectedPlayerIds) {
        Lobby lobby = requireLobby(code);
        GameStateResponse response;
        GamePublicResponse eventPayload;
        LobbyResponse lobbyPayload = null;

        synchronized (lobby) {
            Player player = requirePlayerByToken(lobby, reconnectToken);
            GameRound gameRound = requireGameInProgress(lobby);
            validateVote(lobby, gameRound, player, suspectedPlayerIds);
            gameRound.submitVote(player.getId(), suspectedPlayerIds);
            if (gameRound.getPhase() == GamePhase.FINISHED) {
                lobby.finishGame();
                lobbyPayload = LobbyResponse.from(lobby);
            }
            lobby.touch(clock.instant());
            response = responseMapper.toPrivateResponse(lobby, gameRound, player);
            eventPayload = responseMapper.toPublicResponse(lobby, gameRound);
        }

        if (lobbyPayload != null) {
            eventPublisher.publishLobbyUpdated(lobby.getCode(), lobbyPayload);
        }
        eventPublisher.publishGameUpdated(lobby.getCode(), eventPayload);
        return response;
    }

    public LobbyResponse resetGame(String code, String reconnectToken) {
        Lobby lobby = requireLobby(code);
        LobbyResponse response;
        synchronized (lobby) {
            Player player = requirePlayerByToken(lobby, reconnectToken);
            requireHost(lobby, player, "ONLY_HOST_CAN_RESET_GAME", "Only the host can return to the lobby.");
            if (lobby.getStatus() != LobbyStatus.FINISHED) {
                throw GameStateException.conflict("GAME_NOT_FINISHED", "The game must finish before returning to the lobby.");
            }
            lobby.resetGame();
            lobby.touch(clock.instant());
            response = LobbyResponse.from(lobby);
        }
        eventPublisher.publishLobbyUpdated(lobby.getCode(), response);
        return response;
    }

    private void validateVote(Lobby lobby, GameRound gameRound, Player player, List<UUID> suspectedPlayerIds) {
        if (gameRound.getPhase() != GamePhase.VOTING) {
            throw GameStateException.conflict("VOTING_NOT_ACTIVE", "Voting is not active yet.");
        }
        if (gameRound.hasVoted(player.getId())) {
            throw GameStateException.conflict("ALREADY_VOTED", "You have already submitted your vote.");
        }
        int requiredCount = lobby.getSettings().getImpostorCount();
        if (suspectedPlayerIds == null || suspectedPlayerIds.size() != requiredCount
                || new LinkedHashSet<>(suspectedPlayerIds).size() != requiredCount) {
            throw GameStateException.conflict(
                    "INVALID_VOTE_COUNT",
                    "Select exactly " + requiredCount + " different suspected players."
            );
        }
        Set<UUID> validIds = lobby.getPlayers().stream().map(Player::getId).collect(Collectors.toSet());
        if (!validIds.containsAll(suspectedPlayerIds) || suspectedPlayerIds.contains(player.getId())) {
            throw GameStateException.conflict(
                    "INVALID_VOTE_TARGET",
                    "Votes must target other players in this game."
            );
        }
    }

    private Lobby requireLobby(String code) {
        if (code == null || !LOBBY_CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidLobbyCodeException();
        }
        return lobbyStore.findByCode(code).orElseThrow(LobbyNotFoundException::new);
    }

    private Player requirePlayerByToken(Lobby lobby, String reconnectToken) {
        if (reconnectToken == null || reconnectToken.isBlank()) {
            throw new UnauthorizedPlayerTokenException();
        }
        return lobby.findPlayerByReconnectToken(reconnectToken)
                .orElseThrow(UnauthorizedPlayerTokenException::new);
    }

    private GameRound requireGame(Lobby lobby) {
        return lobby.getGameRound().orElseThrow(() -> GameStateException.conflict(
                "GAME_NOT_STARTED",
                "The game has not started."
        ));
    }

    private GameRound requireGameInProgress(Lobby lobby) {
        if (lobby.getStatus() != LobbyStatus.IN_GAME) {
            throw GameStateException.conflict("GAME_NOT_IN_PROGRESS", "The game is not in progress.");
        }
        return requireGame(lobby);
    }

    private void requireHost(Lobby lobby, Player player, String code, String message) {
        if (!player.getId().equals(lobby.getHostPlayerId())) {
            throw GameStateException.forbidden(code, message);
        }
    }
}

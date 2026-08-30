package com.sevaa05.underthemask.lobby.model;

import com.sevaa05.underthemask.game.model.GameRound;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Lobby {

    public static final int MAX_PLAYERS = 12;
    public static final int MIN_PLAYERS = 3;

    private final String code;
    private final LinkedHashMap<UUID, Player> players = new LinkedHashMap<>();
    private final Instant createdAt;
    private GameSettings settings;
    private LobbyStatus status;
    private GameRound gameRound;
    private UUID hostPlayerId;
    private Instant lastActivityAt;

    public Lobby(String code, GameSettings settings, Instant now) {
        this.code = Objects.requireNonNull(code, "code is required.");
        this.settings = Objects.requireNonNull(settings, "settings are required.");
        this.createdAt = Objects.requireNonNull(now, "now is required.");
        this.lastActivityAt = now;
        this.status = LobbyStatus.WAITING;
    }

    public String getCode() {
        return code;
    }

    public GameSettings getSettings() {
        return settings;
    }

    public void setSettings(GameSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings are required.");
    }

    public LobbyStatus getStatus() {
        return status;
    }

    public void setStatus(LobbyStatus status) {
        this.status = Objects.requireNonNull(status, "status is required.");
    }

    public Optional<GameRound> getGameRound() {
        return Optional.ofNullable(gameRound);
    }

    public void startGame(GameRound gameRound) {
        this.gameRound = Objects.requireNonNull(gameRound, "gameRound is required.");
        this.status = LobbyStatus.IN_GAME;
    }

    public void finishGame() {
        this.status = LobbyStatus.FINISHED;
    }

    public void resetGame() {
        this.gameRound = null;
        this.status = LobbyStatus.WAITING;
    }

    public UUID getHostPlayerId() {
        return hostPlayerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public List<Player> getPlayers() {
        return List.copyOf(players.values());
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean hasPlayerNamed(String playerName) {
        return players.values().stream().anyMatch(player -> player.getName().equalsIgnoreCase(playerName));
    }

    public boolean hasReconnectToken(String token) {
        return players.values().stream().anyMatch(player -> player.getReconnectToken().equals(token));
    }

    public Optional<Player> findPlayerByReconnectToken(String token) {
        return players.values().stream()
                .filter(player -> player.getReconnectToken().equals(token))
                .findFirst();
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
        if (hostPlayerId == null) {
            hostPlayerId = player.getId();
        }
    }

    public Optional<Player> removePlayer(UUID playerId) {
        Player removed = players.remove(playerId);
        if (removed != null && removed.getId().equals(hostPlayerId)) {
            hostPlayerId = players.values().stream()
                    .findFirst()
                    .map(Player::getId)
                    .orElse(null);
        }
        return Optional.ofNullable(removed);
    }

    public void close() {
        status = LobbyStatus.CLOSED;
    }

    public void touch(Instant now) {
        lastActivityAt = now;
    }

    public boolean isExpired(Instant now, Duration maxLifetime, Duration inactiveTimeout) {
        return status == LobbyStatus.CLOSED
                || createdAt.plus(maxLifetime).isBefore(now)
                || lastActivityAt.plus(inactiveTimeout).isBefore(now);
    }
}

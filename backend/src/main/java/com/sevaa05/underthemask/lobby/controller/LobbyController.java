package com.sevaa05.underthemask.lobby.controller;

import com.sevaa05.underthemask.common.auth.BearerTokenExtractor;
import com.sevaa05.underthemask.lobby.dto.CreateLobbyRequest;
import com.sevaa05.underthemask.lobby.dto.JoinLobbyRequest;
import com.sevaa05.underthemask.lobby.dto.LobbyResponse;
import com.sevaa05.underthemask.lobby.dto.LobbySessionResponse;
import com.sevaa05.underthemask.lobby.dto.UpdateSettingsRequest;
import com.sevaa05.underthemask.lobby.model.GameSettings;
import com.sevaa05.underthemask.lobby.service.LobbyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyService lobbyService;
    private final BearerTokenExtractor tokenExtractor;

    public LobbyController(LobbyService lobbyService, BearerTokenExtractor tokenExtractor) {
        this.lobbyService = lobbyService;
        this.tokenExtractor = tokenExtractor;
    }

    @PostMapping
    public ResponseEntity<LobbySessionResponse> createLobby(@Valid @RequestBody CreateLobbyRequest request) {
        GameSettings settings = new GameSettings(request.impostorCount(), request.hintType());
        LobbySessionResponse response = LobbySessionResponse.from(lobbyService.createLobby(request.hostName(), settings));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/players")
    public ResponseEntity<LobbySessionResponse> joinLobby(@PathVariable String code,
                                                          @Valid @RequestBody JoinLobbyRequest request) {
        LobbySessionResponse response = LobbySessionResponse.from(lobbyService.joinLobby(code, request.playerName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    public LobbyResponse getLobby(@PathVariable String code) {
        return lobbyService.getLobby(code);
    }

    @PostMapping("/{code}/reconnect")
    public LobbySessionResponse reconnect(@PathVariable String code,
                                          @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                          String authorization) {
        return LobbySessionResponse.from(lobbyService.reconnect(code, tokenExtractor.extract(authorization)));
    }

    @DeleteMapping("/{code}/players/me")
    public ResponseEntity<Void> leaveLobby(@PathVariable String code,
                                           @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                           String authorization) {
        lobbyService.leaveLobby(code, tokenExtractor.extract(authorization));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{code}/settings")
    public LobbyResponse updateSettings(@PathVariable String code,
                                        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                        String authorization,
                                        @Valid @RequestBody UpdateSettingsRequest request) {
        GameSettings settings = new GameSettings(request.impostorCount(), request.hintType());
        return lobbyService.updateSettings(code, tokenExtractor.extract(authorization), settings);
    }
}

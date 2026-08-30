package com.sevaa05.underthemask.game.controller;

import com.sevaa05.underthemask.common.auth.BearerTokenExtractor;
import com.sevaa05.underthemask.game.dto.GameStateResponse;
import com.sevaa05.underthemask.game.dto.SubmitClueRequest;
import com.sevaa05.underthemask.game.dto.SubmitVoteRequest;
import com.sevaa05.underthemask.game.service.GameService;
import com.sevaa05.underthemask.lobby.dto.LobbyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lobbies/{code}/game")
public class GameController {

    private final GameService gameService;
    private final BearerTokenExtractor tokenExtractor;

    public GameController(GameService gameService, BearerTokenExtractor tokenExtractor) {
        this.gameService = gameService;
        this.tokenExtractor = tokenExtractor;
    }

    @PostMapping("/start")
    public GameStateResponse startGame(@PathVariable String code,
                                       @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                       String authorization) {
        return gameService.startGame(code, tokenExtractor.extract(authorization));
    }

    @GetMapping
    public GameStateResponse getGame(@PathVariable String code,
                                     @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                     String authorization) {
        return gameService.getGame(code, tokenExtractor.extract(authorization));
    }

    @PostMapping("/clues")
    public GameStateResponse submitClue(@PathVariable String code,
                                        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                        String authorization,
                                        @Valid @RequestBody SubmitClueRequest request) {
        return gameService.submitClue(code, tokenExtractor.extract(authorization), request.clue());
    }

    @PostMapping("/votes")
    public GameStateResponse submitVote(@PathVariable String code,
                                        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                        String authorization,
                                        @Valid @RequestBody SubmitVoteRequest request) {
        return gameService.submitVote(code, tokenExtractor.extract(authorization), request.suspectedPlayerIds());
    }

    @PostMapping("/reset")
    public LobbyResponse resetGame(@PathVariable String code,
                                   @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                   String authorization) {
        return gameService.resetGame(code, tokenExtractor.extract(authorization));
    }
}

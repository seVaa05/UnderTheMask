package com.sevaa05.underthemask.common.auth;

import com.sevaa05.underthemask.lobby.service.exception.UnauthorizedPlayerTokenException;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    public String extract(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedPlayerTokenException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedPlayerTokenException();
        }
        return token;
    }
}

package com.sevaa05.underthemask.realtime.service;

public interface RealtimeEventPublisher {

    void publishLobbyUpdated(String lobbyCode, Object payload);

    void publishGameUpdated(String lobbyCode, Object payload);
}

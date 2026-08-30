package com.sevaa05.underthemask.realtime.service;

public class NoOpRealtimeEventPublisher implements RealtimeEventPublisher {

    @Override
    public void publishLobbyUpdated(String lobbyCode, Object payload) {
    }

    @Override
    public void publishGameUpdated(String lobbyCode, Object payload) {
    }
}

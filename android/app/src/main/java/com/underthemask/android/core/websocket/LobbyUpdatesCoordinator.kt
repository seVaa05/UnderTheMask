package com.underthemask.android.core.websocket

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LobbyUpdatesCoordinator @Inject constructor(
    private val realtimeClient: LobbyRealtimeClient,
) {
    private val lifecycleMutex = Mutex()
    private val refreshMutex = Mutex()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var realtimeSession: LobbyRealtimeSession? = null
    private var observerJob: Job? = null

    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    suspend fun start(
        scope: CoroutineScope,
        lobbyCode: String,
        onEvent: suspend (LobbyRealtimeEvent) -> Unit,
        onPollingFallback: suspend () -> Unit,
    ) {
        lifecycleMutex.withLock {
            if (observerJob?.isActive == true) return
            stopLocked()
            val session = realtimeClient.connect(lobbyCode)
            realtimeSession = session
            observerJob = scope.launch {
                launch {
                    session.connectionState.collectLatest { _connectionState.value = it }
                }
                launch { session.events.collect(onEvent) }
                launch {
                    while (currentCoroutineContext().isActive) {
                        delay(POLLING_INTERVAL_MS)
                        if (session.connectionState.value != ConnectionState.CONNECTED) {
                            onPollingFallback()
                        }
                    }
                }
            }
        }
    }

    suspend fun stop() {
        lifecycleMutex.withLock { stopLocked() }
    }

    suspend fun <T> serializedRefresh(block: suspend () -> T): T = refreshMutex.withLock { block() }

    private suspend fun stopLocked() {
        observerJob?.cancelAndJoin()
        observerJob = null
        realtimeSession?.close()
        realtimeSession = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private companion object {
        const val POLLING_INTERVAL_MS = 8_000L
    }
}

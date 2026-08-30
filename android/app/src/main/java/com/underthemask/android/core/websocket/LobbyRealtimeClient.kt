package com.underthemask.android.core.websocket

import com.underthemask.android.core.config.BackendConfig
import com.underthemask.android.core.di.ApplicationScope
import com.underthemask.android.core.network.RealtimeEventTypeDto
import com.underthemask.android.core.network.RealtimeSignalDto
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
enum class LobbyRealtimeEvent { LOBBY_UPDATED, GAME_UPDATED }

interface LobbyRealtimeSession {
    val connectionState: StateFlow<ConnectionState>
    val events: SharedFlow<LobbyRealtimeEvent>
    suspend fun close()
}

interface LobbyRealtimeClient {
    suspend fun connect(lobbyCode: String): LobbyRealtimeSession
}

interface RealtimeConnection {
    suspend fun messages(destination: String): Flow<String>
    suspend fun disconnect()
}

interface RealtimeConnectionFactory {
    suspend fun connect(): RealtimeConnection
}

@Singleton
class StompConnectionFactory @Inject constructor(
    @Named("websocket") okHttpClient: OkHttpClient,
) : RealtimeConnectionFactory {
    private val client = StompClient(OkHttpWebSocketClient(okHttpClient))

    override suspend fun connect(): RealtimeConnection = StompConnection(client.connect(BackendConfig.wsUrl))
}

private class StompConnection(private val session: StompSession) : RealtimeConnection {
    override suspend fun messages(destination: String): Flow<String> = session.subscribeText(destination)
    override suspend fun disconnect() = session.disconnect()
}

@Singleton
class StompLobbyClient @Inject constructor(
    private val connectionFactory: RealtimeConnectionFactory,
    private val eventParser: RealtimeEventParser,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LobbyRealtimeClient {
    private val controlMutex = Mutex()
    private var activeSession: OwnedLobbyRealtimeSession? = null

    override suspend fun connect(lobbyCode: String): LobbyRealtimeSession {
        val normalizedCode = lobbyCode.trim().uppercase()
        return controlMutex.withLock {
            activeSession?.stop()
            OwnedLobbyRealtimeSession(normalizedCode).also { session ->
                activeSession = session
                session.start()
            }
        }
    }

    private suspend fun release(session: OwnedLobbyRealtimeSession) {
        controlMutex.withLock {
            if (activeSession === session) activeSession = null
            session.stop()
        }
    }

    private inner class OwnedLobbyRealtimeSession(
        private val code: String,
    ) : LobbyRealtimeSession {
        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        private val _events = MutableSharedFlow<LobbyRealtimeEvent>(extraBufferCapacity = 16)
        private var connectionJob: Job? = null

        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
        override val events: SharedFlow<LobbyRealtimeEvent> = _events.asSharedFlow()

        fun start() {
            check(connectionJob == null) { "Realtime session is already started." }
            connectionJob = scope.launch { runConnectionLoop() }
        }

        override suspend fun close() = release(this)

        suspend fun stop() {
            connectionJob?.cancelAndJoin()
            connectionJob = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        private suspend fun runConnectionLoop() {
            var retryDelayMs = INITIAL_RETRY_DELAY_MS
            while (currentCoroutineContext().isActive) {
                var connection: RealtimeConnection? = null
                try {
                    _connectionState.value = ConnectionState.CONNECTING
                    connection = connectionFactory.connect()
                    _connectionState.value = ConnectionState.CONNECTED
                    retryDelayMs = INITIAL_RETRY_DELAY_MS
                    connection.messages("/topic/lobbies/$code").collect(::handleMessage)
                    _connectionState.value = ConnectionState.ERROR
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    _connectionState.value = ConnectionState.ERROR
                } finally {
                    if (connection != null) {
                        withContext(NonCancellable) {
                            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                                runCatching { connection.disconnect() }
                            }
                        }
                    }
                }

                _connectionState.value = ConnectionState.DISCONNECTED
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }

        private fun handleMessage(body: String) {
            eventParser.parse(body)?.let(_events::tryEmit)
        }
    }

    private companion object {
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 10_000L
        const val DISCONNECT_TIMEOUT_MS = 1_500L
    }
}

class RealtimeEventParser @Inject constructor(private val json: Json) {
    fun parse(body: String): LobbyRealtimeEvent? {
        val signal = runCatching { json.decodeFromString<RealtimeSignalDto>(body) }.getOrNull() ?: return null
        return when (signal.type) {
            RealtimeEventTypeDto.LOBBY_UPDATED -> LobbyRealtimeEvent.LOBBY_UPDATED
            RealtimeEventTypeDto.GAME_UPDATED -> LobbyRealtimeEvent.GAME_UPDATED
        }
    }
}

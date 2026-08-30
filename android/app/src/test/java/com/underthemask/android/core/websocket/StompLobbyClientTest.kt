package com.underthemask.android.core.websocket

import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StompLobbyClientTest {
    @Test
    fun `connect and close own session`() = runTest {
        val connection = FakeConnection { awaitCancellation() }
        val client = client(FakeConnectionFactory(connection))

        val session = client.connect("abc234")
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, session.connectionState.value)

        session.close()

        assertEquals(ConnectionState.DISCONNECTED, session.connectionState.value)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `closing stale session does not interrupt newer session`() = runTest {
        val firstConnection = FakeConnection { awaitCancellation() }
        val secondConnection = FakeConnection { awaitCancellation() }
        val client = client(FakeConnectionFactory(firstConnection, secondConnection))

        val firstSession = client.connect("abc234")
        runCurrent()
        val secondSession = client.connect("abc234")
        runCurrent()
        firstSession.close()
        runCurrent()

        assertTrue(firstConnection.disconnected)
        assertFalse(secondConnection.disconnected)
        assertEquals(ConnectionState.CONNECTED, secondSession.connectionState.value)
        secondSession.close()
    }

    @Test
    fun `connection loop reconnects after transport failure`() = runTest {
        val failedConnection = FakeConnection { throw IOException("connection lost") }
        val recoveredConnection = FakeConnection { awaitCancellation() }
        val factory = FakeConnectionFactory(failedConnection, recoveredConnection)
        val client = client(factory)

        val session = client.connect("abc234")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(2, factory.connectCount)
        assertTrue(failedConnection.disconnected)
        assertEquals(ConnectionState.CONNECTED, session.connectionState.value)
        session.close()
    }

    @Test
    fun `close cancels message collection and disconnects transport`() = runTest {
        var collectionCancelled = false
        val connection = FakeConnection {
            try {
                awaitCancellation()
            } finally {
                collectionCancelled = true
            }
        }
        val client = client(FakeConnectionFactory(connection))
        val session = client.connect("abc234")
        runCurrent()

        session.close()

        assertTrue(collectionCancelled)
        assertTrue(connection.disconnected)
    }

    private fun kotlinx.coroutines.test.TestScope.client(factory: RealtimeConnectionFactory) =
        StompLobbyClient(
            connectionFactory = factory,
            eventParser = RealtimeEventParser(Json { ignoreUnknownKeys = true }),
            scope = backgroundScope,
        )

    private class FakeConnectionFactory(
        vararg connections: FakeConnection,
    ) : RealtimeConnectionFactory {
        private val queuedConnections = ArrayDeque(connections.toList())
        var connectCount = 0

        override suspend fun connect(): RealtimeConnection {
            connectCount += 1
            return queuedConnections.removeFirst()
        }
    }

    private class FakeConnection(
        private val collectMessages: suspend () -> Unit,
    ) : RealtimeConnection {
        var disconnected = false

        override suspend fun messages(destination: String): Flow<String> = flow { collectMessages() }

        override suspend fun disconnect() {
            disconnected = true
        }
    }
}

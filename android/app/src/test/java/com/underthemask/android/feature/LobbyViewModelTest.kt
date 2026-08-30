package com.underthemask.android.feature

import androidx.lifecycle.SavedStateHandle
import com.underthemask.android.core.model.LobbyStatus
import com.underthemask.android.core.websocket.ConnectionState
import com.underthemask.android.core.websocket.LobbyUpdatesCoordinator
import com.underthemask.android.feature.lobby.LobbyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModelTest {
    @Test
    fun `initial REST load renders content without a realtime event`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val lobbyRepository = FakeLobbyRepository().apply {
                lobby = sampleLobby().copy(status = LobbyStatus.WAITING)
            }
            val viewModel = LobbyViewModel(
                savedStateHandle = SavedStateHandle(mapOf("code" to "ABC234")),
                lobbyRepository = lobbyRepository,
                gameRepository = FakeGameRepository(),
                updatesCoordinator = LobbyUpdatesCoordinator(FakeRealtimeClient()),
            )

            assertEquals(1, lobbyRepository.getLobbyCalls)
            assertFalse(viewModel.state.value.isLoading)
            assertNotNull(viewModel.state.value.lobby)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `disconnected realtime state does not hide loaded REST content`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val lobbyRepository = FakeLobbyRepository().apply {
                lobby = sampleLobby().copy(status = LobbyStatus.WAITING)
            }
            val viewModel = LobbyViewModel(
                savedStateHandle = SavedStateHandle(mapOf("code" to "ABC234")),
                lobbyRepository = lobbyRepository,
                gameRepository = FakeGameRepository(),
                updatesCoordinator = LobbyUpdatesCoordinator(FakeRealtimeClient()),
            )

            assertEquals(ConnectionState.DISCONNECTED, viewModel.state.value.connectionState)
            assertNotNull(viewModel.state.value.lobby)
            assertFalse(viewModel.state.value.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

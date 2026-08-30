package com.underthemask.android.feature

import androidx.lifecycle.SavedStateHandle
import com.underthemask.android.feature.game.GameViewModel
import com.underthemask.android.core.websocket.LobbyUpdatesCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @Test
    fun `voting state caps selection and submits exact required count`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val gameRepository = FakeGameRepository()
            val viewModel = GameViewModel(
                savedStateHandle = SavedStateHandle(mapOf("code" to "ABC234")),
                lobbyRepository = FakeLobbyRepository(),
                gameRepository = gameRepository,
                updatesCoordinator = LobbyUpdatesCoordinator(FakeRealtimeClient()),
            )

            viewModel.toggleSuspect("p2")
            viewModel.toggleSuspect("p3")
            viewModel.toggleSuspect("p4")

            assertEquals(setOf("p2", "p3"), viewModel.state.value.selectedPlayerIds)
            assertTrue(viewModel.state.value.canSubmitVote)

            viewModel.submitVote()

            assertEquals(setOf("p2", "p3"), gameRepository.lastVote?.toSet())
            assertTrue(viewModel.state.value.gameState?.hasSubmittedVote == true)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

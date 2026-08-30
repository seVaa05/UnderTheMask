package com.underthemask.android.feature.lobby

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.underthemask.android.core.model.HintType
import com.underthemask.android.core.model.Lobby
import com.underthemask.android.core.model.LobbyStatus
import com.underthemask.android.core.model.Player
import com.underthemask.android.core.ui.AppBackground
import com.underthemask.android.core.ui.AppConfirmDialog
import com.underthemask.android.core.ui.AppPanel
import com.underthemask.android.core.ui.AppTopBar
import com.underthemask.android.core.ui.BottomActionSurface
import com.underthemask.android.core.ui.ConnectionBanner
import com.underthemask.android.core.ui.InlineError
import com.underthemask.android.core.ui.LoadingScreen
import com.underthemask.android.core.ui.PlayerAvatar
import com.underthemask.android.core.ui.PrimaryAction
import com.underthemask.android.core.ui.RealtimeLifecycle
import com.underthemask.android.core.ui.SectionHeader
import com.underthemask.android.core.ui.SegmentedChoice
import com.underthemask.android.core.ui.StatusPill
import com.underthemask.android.core.ui.theme.AppColors

@Composable
fun LobbyScreen(
    state: LobbyUiState,
    onScreenStarted: () -> Unit,
    onScreenStopped: () -> Unit,
    onImpostorCountChange: (Int) -> Unit,
    onHintTypeChange: (HintType) -> Unit,
    onStartGame: () -> Unit,
    onLeave: () -> Unit,
    onDismissError: () -> Unit,
) {
    RealtimeLifecycle(onScreenStarted, onScreenStopped)
    var showLeaveDialog by remember { mutableStateOf(false) }
    val lobby = state.lobby

    if (state.isLoading && lobby == null) {
        LoadingScreen("Učitavam lobby...")
        return
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                AppTopBar(
                    title = lobby?.lobbyCode ?: "------",
                    eyebrow = "Lobby kod",
                    actionLabel = "Izađi",
                    onAction = { showLeaveDialog = true },
                )
            },
            bottomBar = {
                BottomActionSurface {
                    if (state.isHost) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PrimaryAction(
                                text = if (state.isActionPending) "Obrada..." else "Pokreni igru",
                                enabled = state.canStart,
                                onClick = onStartGame,
                            )
                            if (!state.canStart && !state.isActionPending && lobby != null) {
                                Text(
                                    "Potrebno je najmanje ${lobby.minimumPlayers} igrača.",
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        WaitingFooter("Čekaš hosta da pokrene igru")
                    }
                }
            },
        ) { padding ->
            if (lobby != null) {
                LobbyContent(
                    lobby = lobby,
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onImpostorCountChange = onImpostorCountChange,
                    onHintTypeChange = onHintTypeChange,
                    onDismissError = onDismissError,
                )
            } else {
                state.errorMessage?.let { message ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        InlineError(message)
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AppConfirmDialog(
            title = "Napustiti lobby?",
            message = "Tvoja sesija na ovom uređaju biće uklonjena. Ako je runda aktivna, ostali igrači vraćaju se u lobby.",
            confirmLabel = "Napusti",
            confirmEnabled = !state.isActionPending,
            onConfirm = {
                showLeaveDialog = false
                onLeave()
            },
            onDismiss = { showLeaveDialog = false },
        )
    }
}

@Composable
private fun LobbyContent(
    lobby: Lobby,
    state: LobbyUiState,
    modifier: Modifier,
    onImpostorCountChange: (Int) -> Unit,
    onHintTypeChange: (HintType) -> Unit,
    onDismissError: () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val useTwoColumns = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "content") {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ConnectionBanner(state.connectionState)
                    state.errorMessage?.let { InlineError(it, onDismissError) }
                    LobbySummary(lobby)

                    if (useTwoColumns) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            PlayerSection(lobby, state.playerId, Modifier.weight(1.18f))
                            SettingsSection(
                                lobby = lobby,
                                state = state,
                                modifier = Modifier.weight(0.82f),
                                onImpostorCountChange = onImpostorCountChange,
                                onHintTypeChange = onHintTypeChange,
                            )
                        }
                    } else {
                        PlayerSection(lobby, state.playerId)
                        SettingsSection(
                            lobby = lobby,
                            state = state,
                            onImpostorCountChange = onImpostorCountChange,
                            onHintTypeChange = onHintTypeChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbySummary(lobby: Lobby) {
    AppPanel(highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SOBA JE SPREMNA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    lobby.lobbyCode,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                )
                Text("Podeli ovaj kod sa ekipom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(lobby.status.displayName())
                Text(
                    "${lobby.playerCount}/${lobby.maxPlayers} igrača",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayerSection(lobby: Lobby, playerId: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Ekipa", "Igrači", "${lobby.playerCount}/${lobby.maxPlayers}")
        lobby.players.forEach { player ->
            PlayerRow(player, isCurrentPlayer = player.playerId == playerId)
        }
    }
}

@Composable
private fun SettingsSection(
    lobby: Lobby,
    state: LobbyUiState,
    modifier: Modifier = Modifier,
    onImpostorCountChange: (Int) -> Unit,
    onHintTypeChange: (HintType) -> Unit,
) {
    AppPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionHeader(
                eyebrow = "Pravila",
                title = "Podešavanja",
                trailing = if (state.isHost) "Host kontrola" else "Samo pregled",
            )
            SegmentedChoice(
                label = "Broj impostora",
                selected = lobby.settings.impostorCount,
                options = listOf(1 to "1", 2 to "2"),
                enabled = state.isHost && !state.isActionPending,
                onSelected = onImpostorCountChange,
            )
            SegmentedChoice(
                label = "Pomoć za impostora",
                selected = lobby.settings.hintType,
                options = listOf(HintType.CATEGORY to "Kategorija", HintType.ASSOCIATION to "Asocijacija"),
                enabled = state.isHost && !state.isActionPending,
                onSelected = onHintTypeChange,
            )
        }
    }
}

@Composable
private fun PlayerRow(player: Player, isCurrentPlayer: Boolean) {
    AppPanel(
        highlighted = isCurrentPlayer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerAvatar(player.playerName, emphasized = isCurrentPlayer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(player.playerName, style = MaterialTheme.typography.titleMedium)
                    if (player.isHost) {
                        Text(
                            "♛",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { contentDescription = "Host" },
                        )
                    }
                    if (isCurrentPlayer) StatusPill("Ti")
                }
                Text(
                    if (player.isHost) "Vodi ovu partiju" else "Spreman za rundu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(
                    Modifier.size(8.dp).semantics {
                        contentDescription = if (player.connected) "Povezan" else "Nije povezan"
                    },
                ) {
                    drawCircle(if (player.connected) AppColors.Success else AppColors.Error)
                }
                Text(
                    if (player.connected) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (player.connected) AppColors.Success else AppColors.Error,
                )
            }
        }
    }
}

@Composable
private fun WaitingFooter(message: String) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(accentColor) }
        Text(
            message,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LobbyStatus.displayName() = when (this) {
    LobbyStatus.WAITING -> "Čekanje"
    LobbyStatus.IN_GAME -> "U igri"
    LobbyStatus.FINISHED -> "Završeno"
    LobbyStatus.CLOSED -> "Zatvoreno"
}

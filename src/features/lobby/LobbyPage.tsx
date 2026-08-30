import { useEffect, useMemo, useState } from 'react';
import { Link, useHistory, useParams } from 'react-router-dom';
import { ArrowLeft, Check, Copy, Crown, LogOut, Play, Radio, RefreshCw, Settings2, Users } from 'lucide-react';
import { GameView } from '../game/GameView';
import {
  getGame,
  resetGame,
  startGame,
  submitClue,
  submitVote,
} from '../../shared/api/gameService';
import {
  getApiErrorMessage,
  getLobby,
  leaveLobby,
  reconnectToLobby,
  updateLobbySettings,
} from '../../shared/api/lobbyService';
import type { GameStateResponse, LobbyResponse, LobbySession } from '../../shared/api/types';
import { Button } from '../../shared/components/Button';
import { BrandMark } from '../../shared/components/BrandMark';
import { SegmentedControl } from '../../shared/components/SegmentedControl';
import { createLobbyRealtimeClient, type LobbyConnectionStatus } from '../../shared/realtime/lobbyRealtime';
import { clearLobbySession, readLobbySession, saveLobbySession } from '../../shared/storage/sessionStorage';

export function LobbyPage() {
  const { code = '' } = useParams<{ code: string }>();
  const history = useHistory();
  const normalizedCode = code.toUpperCase();
  const [lobby, setLobby] = useState<LobbyResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLeaving, setIsLeaving] = useState(false);
  const [isGameActionPending, setIsGameActionPending] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [connectionStatus, setConnectionStatus] = useState<LobbyConnectionStatus>('connecting');
  const [session, setSession] = useState<LobbySession | null>(() => readLobbySession(normalizedCode));
  const [gameState, setGameState] = useState<GameStateResponse | null>(null);
  const hasLobby = Boolean(lobby);

  const currentPlayer = useMemo(
    () => lobby?.players.find((player) => player.playerId === session?.playerId),
    [lobby?.players, session?.playerId],
  );
  const isHost = Boolean(session?.playerId && lobby?.hostPlayerId === session.playerId);
  const connectedPlayers = lobby?.players.filter((player) => player.connected).length ?? 0;

  useEffect(() => {
    let ignore = false;

    async function loadLobby() {
      setIsLoading(true);
      setErrorMessage('');

      try {
        const storedSession = readLobbySession(normalizedCode);

        if (storedSession?.lobbyCode === normalizedCode) {
          const reconnectedSession = await reconnectToLobby(normalizedCode);
          saveLobbySession(reconnectedSession);
          if (!ignore) {
            setSession(reconnectedSession);
          }
        } else if (!ignore) {
          setSession(null);
        }

        const result = await getLobby(normalizedCode);
        let loadedGame: GameStateResponse | null = null;
        if (storedSession?.lobbyCode === normalizedCode && result.status !== 'WAITING') {
          loadedGame = await getGame(normalizedCode);
        }

        if (!ignore) {
          setLobby(result);
          setGameState(loadedGame);
        }
      } catch (error) {
        if (!ignore) {
          setErrorMessage(getApiErrorMessage(error));
        }
      } finally {
        if (!ignore) {
          setIsLoading(false);
        }
      }
    }

    loadLobby();

    return () => {
      ignore = true;
    };
  }, [normalizedCode]);

  useEffect(() => {
    if (!hasLobby) {
      return undefined;
    }

    const realtime = createLobbyRealtimeClient(normalizedCode, {
      onLobbyUpdated: (updatedLobby) => {
        setLobby(updatedLobby);
        if (updatedLobby.status === 'WAITING') {
          setGameState(null);
        }
      },
      onGameUpdated: () => {
        if (readLobbySession(normalizedCode)?.lobbyCode !== normalizedCode) {
          return;
        }
        void getGame(normalizedCode)
          .then(setGameState)
          .catch((error) => setErrorMessage(getApiErrorMessage(error)));
      },
      onStatusChange: setConnectionStatus,
    });

    realtime.activate();

    return () => {
      void realtime.deactivate();
    };
  }, [hasLobby, normalizedCode]);

  async function handleSettingsChange<TKey extends keyof LobbyResponse['settings']>(
    key: TKey,
    value: LobbyResponse['settings'][TKey],
  ) {
    if (!lobby) {
      return;
    }

    const nextSettings = {
      ...lobby.settings,
      [key]: value,
    };

    setLobby({ ...lobby, settings: nextSettings });
    setErrorMessage('');

    try {
      const updatedLobby = await updateLobbySettings(lobby.lobbyCode, nextSettings);
      setLobby(updatedLobby);
    } catch (error) {
      setLobby(lobby);
      setErrorMessage(getApiErrorMessage(error));
    }
  }

  async function handleLeave() {
    if (!lobby) {
      return;
    }

    setIsLeaving(true);
    setErrorMessage('');

    try {
      await leaveLobby(lobby.lobbyCode);
      clearLobbySession();
      setSession(null);
      history.push('/');
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsLeaving(false);
    }
  }

  async function handleStartGame() {
    setIsGameActionPending(true);
    setErrorMessage('');
    try {
      const state = await startGame(normalizedCode);
      setGameState(state);
      setLobby((current) => current ? { ...current, status: 'IN_GAME' } : current);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsGameActionPending(false);
    }
  }

  async function handleSubmitClue(clue: string) {
    setIsGameActionPending(true);
    setErrorMessage('');
    try {
      setGameState(await submitClue(normalizedCode, { clue }));
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsGameActionPending(false);
    }
  }

  async function handleSubmitVote(suspectedPlayerIds: string[]) {
    setIsGameActionPending(true);
    setErrorMessage('');
    try {
      setGameState(await submitVote(normalizedCode, { suspectedPlayerIds }));
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsGameActionPending(false);
    }
  }

  async function handleResetGame() {
    setIsGameActionPending(true);
    setErrorMessage('');
    try {
      const resetLobby = await resetGame(normalizedCode);
      setLobby(resetLobby);
      setGameState(null);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsGameActionPending(false);
    }
  }

  if (isLoading || !lobby) {
    return (
      <section className="screen lobby-screen center-screen">
        <BrandMark />
        <div className="loading-seal"><RefreshCw className="spin" size={24} /></div>
        <div><h1>Pripremamo sto</h1><p>Učitavamo lobby i vraćamo tvoju sesiju.</p></div>
        {errorMessage ? <div className="form-error" role="alert">{errorMessage}</div> : null}
      </section>
    );
  }

  if (gameState && session && lobby.status !== 'WAITING') {
    return (
      <GameView
        lobbyCode={normalizedCode}
        playerId={session.playerId}
        isHost={isHost}
        state={gameState}
        connectionStatus={connectionStatus}
        errorMessage={errorMessage}
        isBusy={isGameActionPending || isLeaving}
        onSubmitClue={handleSubmitClue}
        onSubmitVote={handleSubmitVote}
        onReset={handleResetGame}
        onLeave={handleLeave}
      />
    );
  }

  return (
    <section className="screen lobby-screen">
      <header className="screen-topbar lobby-topbar">
        <BrandMark linkToHome />
        <div className="topbar-actions">
        <Link to="/" className="back-link compact">
          <ArrowLeft size={18} />
          Početna
        </Link>
        <Button
          type="button"
          variant="ghost"
          className="small-button"
          icon={<LogOut size={17} />}
          disabled={isLeaving}
          onClick={handleLeave}
        >
          {isLeaving ? 'Izlazim...' : 'Izađi'}
        </Button>
        </div>
      </header>

      {connectionStatus !== 'connected' ? (
        <div className={`connection-note ${connectionStatus}`} role="status">
          <RefreshCw className={connectionStatus === 'connecting' ? 'spin' : ''} size={16} />
          <div><strong>{connectionStatus === 'connecting' ? 'Ponovno povezivanje' : 'Veza je prekinuta'}</strong><span>{connectionStatus === 'connecting' ? 'Vraćamo te u lobby...' : 'Pokušavamo da obnovimo real-time vezu.'}</span></div>
        </div>
      ) : null}

      {errorMessage ? <div className="connection-note error" role="alert">{errorMessage}</div> : null}

      <header className="lobby-header">
        <div className="lobby-title-block">
          <p className="eyebrow">Privatna soba</p>
          <div className="lobby-code-title">
            <h1>{lobby.lobbyCode}</h1>
            <Button type="button" variant="ghost" className="icon-button copy-code-button" aria-label="Kopiraj kod lobija" title="Kopiraj kod" icon={<Copy size={18} />} onClick={() => navigator.clipboard?.writeText(lobby.lobbyCode)} />
          </div>
          <p>Podeli kod sa ekipom. Igra počinje kada host pokrene rundu.</p>
        </div>
        <div className="lobby-meta">
          <div className="status-badge ready"><Radio size={15} /> {lobby.status === 'WAITING' ? 'Čeka igrače' : 'U toku'}</div>
          <div className="lobby-stat">
            <Users size={19} />
            <span><strong>{lobby.playerCount}</strong> / {lobby.maxPlayers}</span>
            <small>{connectedPlayers} online</small>
          </div>
        </div>
      </header>

      <div className="lobby-layout">
        <section className="panel players-panel">
          <div className="panel-heading">
            <div><span className="section-icon"><Users size={18} /></span><div><p className="eyebrow">Za stolom</p><h2>Igrači</h2></div></div>
            <span className="count-badge">{lobby.playerCount}/{lobby.maxPlayers}</span>
          </div>

          <ul className="player-list">
            {lobby.players.map((player) => (
              <li key={player.playerId} className={`player-row ${player.playerId === currentPlayer?.playerId ? 'current' : ''} ${!player.connected ? 'offline' : ''}`}>
                <div className="player-avatar" aria-hidden="true">{player.playerName.slice(0, 1).toUpperCase()}</div>
                <div className="player-copy">
                  <strong>{player.playerName}</strong>
                  <span>{player.host ? 'Host sobe' : player.playerId === currentPlayer?.playerId ? 'Tvoj igrač' : 'Igrač'}</span>
                </div>
                <div className="player-flags">
                  {player.host ? <Crown className="host-icon" size={16} aria-label="Host" /> : null}
                  {player.playerId === currentPlayer?.playerId ? <span className="you-badge">Ti</span> : null}
                  <span className={`status-dot ${player.connected ? 'connected' : 'disconnected'}`} title={player.connected ? 'Povezan' : 'Van mreže'} />
                </div>
              </li>
            ))}
          </ul>
        </section>

        <aside className="lobby-control-rail">
          <section className="panel settings-panel">
          <div className="panel-heading compact-heading">
            <div><span className="section-icon"><Settings2 size={18} /></span><div><p className="eyebrow">Pravila runde</p><h2>Podešavanja</h2></div></div>
            {isHost ? <span className="host-control-badge"><Crown size={13} /> Host</span> : null}
          </div>

          <SegmentedControl
            label="Broj impostora"
            disabled={!isHost || lobby.status !== 'WAITING'}
            value={lobby.settings.impostorCount}
            options={[
              { label: '1', value: 1 },
              { label: '2', value: 2 },
            ]}
            onChange={(value) => handleSettingsChange('impostorCount', value)}
          />

          <SegmentedControl
            label="Pomoc za impostora"
            disabled={!isHost || lobby.status !== 'WAITING'}
            value={lobby.settings.hintType}
            options={[
              { label: 'Kategorija', value: 'CATEGORY' },
              { label: 'Asocijacija', value: 'ASSOCIATION' },
            ]}
            onChange={(value) => handleSettingsChange('hintType', value)}
          />

          {!isHost ? <div className="disabled-note"><Crown size={16} /> Samo host može da menja pravila.</div> : null}
          </section>

          <div className={`lobby-action-bar ${isHost ? '' : 'muted'}`}>
            <div className="readiness-copy">
              {isHost ? <Check size={18} /> : <RefreshCw size={18} />}
              <div><strong>{isHost ? (lobby.playerCount >= lobby.minimumPlayers ? 'Spremno za početak' : 'Potrebno je još igrača') : 'Čeka se host'}</strong><span>{isHost ? `${lobby.playerCount}/${lobby.minimumPlayers} minimum igrača` : 'Ostani u sobi do početka.'}</span></div>
            </div>
            {isHost ? (
              <Button type="button" icon={<Play size={18} />} disabled={isGameActionPending || lobby.playerCount < lobby.minimumPlayers || lobby.settings.impostorCount >= lobby.playerCount} onClick={handleStartGame}>
                {isGameActionPending ? 'Pokrećem...' : 'Pokreni igru'}
              </Button>
            ) : null}
          </div>
        </aside>
      </div>
    </section>
  );
}

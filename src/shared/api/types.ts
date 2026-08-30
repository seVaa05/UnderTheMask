export type HintType = 'CATEGORY' | 'ASSOCIATION';

export type LobbyStatus = 'WAITING' | 'IN_GAME' | 'FINISHED' | 'CLOSED';

export interface LobbySettings {
  impostorCount: 1 | 2;
  hintType: HintType;
}

export interface PlayerSummary {
  playerId: string;
  playerName: string;
  connected: boolean;
  host: boolean;
}

export interface LobbyResponse {
  lobbyCode: string;
  status: LobbyStatus;
  hostPlayerId: string;
  settings: LobbySettings;
  players: PlayerSummary[];
  playerCount: number;
  minimumPlayers: number;
  maxPlayers: number;
}

export interface LobbySession {
  lobbyCode: string;
  playerId: string;
  reconnectToken: string;
}

export interface CreateLobbyRequest extends LobbySettings {
  hostName: string;
}

export interface JoinLobbyRequest {
  playerName: string;
}

export interface RealtimeEvent<TPayload> {
  type: 'LOBBY_UPDATED' | 'GAME_UPDATED';
  payload: TPayload;
  occurredAt: string;
}

export type GamePhase = 'CLUES' | 'VOTING' | 'FINISHED';
export type PlayerRole = 'CREWMATE' | 'IMPOSTOR';
export type GameWinner = 'CREWMATES' | 'IMPOSTORS';

export interface GamePlayer {
  playerId: string;
  playerName: string;
  connected: boolean;
}

export interface GameClue {
  playerId: string;
  playerName: string;
  clue: string;
}

export interface VoteTally {
  playerId: string;
  playerName: string;
  votes: number;
}

export interface GameResult {
  winner: GameWinner;
  secretWord: string;
  impostorPlayerIds: string[];
  mostVotedPlayerIds: string[];
  tie: boolean;
  tallies: VoteTally[];
}

export interface GamePublicState {
  roundId: string;
  phase: GamePhase;
  currentPlayerId: string | null;
  players: GamePlayer[];
  clues: GameClue[];
  votesSubmitted: number;
  totalPlayers: number;
  requiredSuspectCount: number;
  result: GameResult | null;
}

export interface GameStateResponse {
  game: GamePublicState;
  role: PlayerRole;
  secretWord: string | null;
  hint: string;
  hasSubmittedVote: boolean;
}

export interface SubmitClueRequest {
  clue: string;
}

export interface SubmitVoteRequest {
  suspectedPlayerIds: string[];
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}

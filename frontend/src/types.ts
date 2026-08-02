export interface ErrorBody {
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

// ---- avatar ----

export interface Avatar {
  avatarColor: string;
  avatarExpression: string;
  avatarSunglasses: boolean;
  avatarWig: string;
}

// ---- guest player / room (GuestDto) ----

export interface GuestPlayer extends Avatar {
  playerId: string;
  nickname: string;
  language: string;
  score: number;
  isHost: boolean;
  isDrawing: boolean;
  isConnected: boolean;
}

export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED';

export interface GuestRoomInfo {
  roomId: string;
  name: string;
  isPrivate: boolean;
  status: RoomStatus;
  hostId: string;
  maxPlayers: number;
  totalRounds: number;
  drawingTimeSec: number;
  customWordsEnabled: boolean;
  currentRound: number;
  playerCount: number;
  customWords: string[];
  players: GuestPlayer[];
}

export interface SessionResponse {
  player: GuestPlayer;
  reconnectToken: string;
  playerId: string;
}

export interface ReconnectResponse {
  player: GuestPlayer;
  roomId: string | null;
}

// ---- game broadcasts (mirror GuestDto) ----

export interface WordOption {
  id: string;
  text: string;
}

export interface WordOptions {
  roundNumber: number;
  options: WordOption[];
}

export interface SecretWord {
  word: string;
}

export interface RoundStarting {
  roundNumber: number;
  drawerId: string;
  drawerName: string;
  optionCount: number;
}

export interface RoundStarted {
  roundNumber: number;
  drawerId: string;
  drawerName: string;
  drawingTimeSec: number;
}

export interface TimerUpdate {
  remainingSeconds: number;
}

export interface CorrectGuess {
  userId: string;
  username: string;
  points: number;
  remainingGuessers: number;
}

export interface RoundEnded {
  roundNumber: number;
  word: string;
  drawerId: string;
  drawerBonus: number;
  scores: Record<string, number>;
}

export interface GameEnded {
  winnerId: string | null;
  winnerName: string | null;
  scores: Record<string, number>;
}

export interface ChatMessage {
  userId: string;
  username: string;
  text: string;
  sentAt: string;
}

export interface TypingIndicator {
  userId: string;
  username: string;
}

export interface Stroke {
  type: 'path';
  points: { x: number; y: number }[];
  color: string;
  size: number;
}

export interface GuestGameState {
  roundNumber: number;
  drawerId: string | null;
  drawerName: string;
  drawingTimeSec: number;
  remainingSeconds: number;
  scores: Record<string, number>;
  secretWord: string | null;
  strokes: Stroke[];
  active: boolean;
}

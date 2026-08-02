import type {
  ErrorBody,
  GuestGameState,
  GuestRoomInfo,
  ReconnectResponse,
  SessionResponse,
} from './types';

const BASE_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? '';

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(body: ErrorBody) {
    super(body.message || 'Request failed');
    this.status = body.status;
    this.code = body.error;
  }
}

async function request<T>(path: string, options: RequestInit = {}, playerId?: string): Promise<T> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) };
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  if (playerId) headers['X-Player-Id'] = playerId;
  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(body ?? { status: res.status, error: 'ERROR', message: 'Request failed' });
  return body as T;
}

export interface GuestSessionInput {
  nickname: string;
  avatarColor: string;
  avatarExpression: string;
  avatarSunglasses: boolean;
  avatarWig: string;
  language: string;
}

export interface CreateRoomInput {
  name: string;
  isPrivate: boolean;
  maxPlayers: number;
  totalRounds: number;
  drawingTimeSec: number;
  customWords: boolean;
  customWordList?: string[];
}

export const guestApi = {
  session: (input: GuestSessionInput) =>
    request<SessionResponse>('/api/guest/session', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  reconnect: (playerId: string, reconnectToken: string) =>
    request<ReconnectResponse>('/api/guest/reconnect', {
      method: 'POST',
      body: JSON.stringify({ playerId, reconnectToken }),
    }),
  listRooms: () => request<GuestRoomInfo[]>('/api/guest/rooms'),
  getRoom: (roomId: string) => request<GuestRoomInfo>(`/api/guest/rooms/${roomId}`),
  createRoom: (playerId: string, input: CreateRoomInput) =>
    request<GuestRoomInfo>('/api/guest/rooms', {
      method: 'POST',
      body: JSON.stringify(input),
    }, playerId),
  joinRoom: (playerId: string, roomId: string) =>
    request<GuestRoomInfo>(`/api/guest/rooms/${roomId}/join`, { method: 'POST' }, playerId),
  leaveRoom: (playerId: string, roomId: string) =>
    request<void>(`/api/guest/rooms/${roomId}/leave`, { method: 'POST' }, playerId),
  startGame: (playerId: string, roomId: string) =>
    request<void>(`/api/guest/rooms/${roomId}/start`, { method: 'POST' }, playerId),
  addWords: (playerId: string, roomId: string, words: string[]) =>
    request<GuestRoomInfo>(`/api/guest/rooms/${roomId}/words`, {
      method: 'POST',
      body: JSON.stringify({ words }),
    }, playerId),
  removeWord: (playerId: string, roomId: string, word: string) =>
    request<GuestRoomInfo>(`/api/guest/rooms/${roomId}/words/remove`, {
      method: 'POST',
      body: JSON.stringify({ word }),
    }, playerId),
  guess: (playerId: string, roomId: string, text: string) =>
    request<{ userId: string; username: string; points: number; remainingGuessers: number } | null>(
      `/api/guest/rooms/${roomId}/guess`,
      { method: 'POST', body: JSON.stringify({ text }) },
      playerId,
    ),
  state: (playerId: string, roomId: string) =>
    request<GuestGameState>(`/api/guest/rooms/${roomId}/state`, {}, playerId),
};

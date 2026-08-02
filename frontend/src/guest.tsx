import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { guestApi } from './api';
import { stomp } from './ws';
import type { GuestPlayer } from './types';

const KEY = 'st_guest';

export interface GuestSession {
  playerId: string;
  reconnectToken: string;
  nickname: string;
  avatarColor: string;
  avatarExpression: string;
  avatarSunglasses: boolean;
  avatarWig: string;
  language: string;
}

export function loadSession(): GuestSession | null {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? 'null');
  } catch {
    return null;
  }
}

function saveSession(s: GuestSession) {
  localStorage.setItem(KEY, JSON.stringify(s));
}

export function clearSession() {
  localStorage.removeItem(KEY);
}

/** Connect the shared STOMP socket for the stored session, if any. Safe to call repeatedly. */
export function connectWS() {
  const session = loadSession();
  if (session) stomp.connect(session.playerId);
}

function toSession(player: GuestPlayer, reconnectToken: string): GuestSession {
  return {
    playerId: player.playerId,
    reconnectToken,
    nickname: player.nickname,
    avatarColor: player.avatarColor,
    avatarExpression: player.avatarExpression,
    avatarSunglasses: player.avatarSunglasses,
    avatarWig: player.avatarWig,
    language: player.language,
  };
}

/** Creates a fresh session on the server and stores it locally. */
export async function createGuestSession(input: {
  nickname: string;
  avatarColor: string;
  avatarExpression: string;
  avatarSunglasses: boolean;
  avatarWig: string;
  language: string;
}): Promise<GuestSession> {
  const res = await guestApi.session(input);
  const session = toSession(res.player, res.reconnectToken);
  saveSession(session);
  stomp.connect(session.playerId);
  return session;
}

/** Returns the stored session if the server still knows it, otherwise null. */
export async function resumeSession(): Promise<{ session: GuestSession; roomId: string | null } | null> {
  const session = loadSession();
  if (!session) return null;
  try {
    const res = await guestApi.reconnect(session.playerId, session.reconnectToken);
    if (!res || !res.player) return null;
    const fresh = toSession(res.player, session.reconnectToken);
    saveSession(fresh);
    stomp.connect(fresh.playerId);
    return { session: fresh, roomId: res.roomId ?? null };
  } catch {
    clearSession();
    return null;
  }
}

export function updateSessionNickname(nickname: string) {
  const s = loadSession();
  if (!s) return;
  saveSession({ ...s, nickname });
}

export function GuestProvider({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

/** Re-exported for convenience; pages that need the live session keep their own state. */
export function useSession() {
  const [session, setSession] = useState<GuestSession | null>(() => loadSession());
  const [roomId, setRoomId] = useState<string | null>(null);

  useEffect(() => {
    resumeSession().then((r) => {
      if (r) {
        setSession(r.session);
        setRoomId(r.roomId);
      } else {
        setSession(null);
      }
    });
  }, []);

  const updateSession = useCallback((s: GuestSession | null) => {
    if (s) saveSession(s);
    else clearSession();
    setSession(s);
  }, []);

  return { session, roomId, updateSession };
}

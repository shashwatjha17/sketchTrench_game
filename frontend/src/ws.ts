import { useEffect, useState } from 'react';
import { Client, ReconnectionTimeMode, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { BACKEND_URL } from './api';

/**
 * Plain WebSocket to Spring's SockJS endpoint ({@code /ws/websocket} is its raw-WebSocket
 * transport). No sockjs-client: it cost ~50 KB of JS and registered an `unload` handler,
 * which is deprecated and disables the browser's back/forward cache.
 */
const WS_URL = (BACKEND_URL || window.location.origin).replace(/^http/, 'ws') + '/ws/websocket';

type Listener = (payload: unknown) => void;

/** idle: never connected · connecting: first attempt · connected · reconnecting: dropped, retrying */
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

/**
 * Single shared STOMP connection over a native WebSocket.
 *
 * - `connect()` is idempotent: calling it again for the same player keeps the live socket
 *   instead of tearing it down (pages call it on mount; the old behaviour opened a second
 *   socket whose predecessor's disconnect marked the player "away").
 * - Reconnects with exponential backoff (1s → 15s) so a sleeping backend that takes a while
 *   to wake up is retried instead of given up on.
 * - Exposes a connection status and an `onConnect` hook so pages can refetch state after a
 *   (re)connect — anything broadcast while we were offline would otherwise be missed.
 */
class StompConnection {
  private client: Client | null = null;
  private playerId: string | null = null;
  private listeners = new Map<string, Set<Listener>>();
  private brokerSubs = new Map<string, StompSubscription>();
  private statusListeners = new Set<(s: ConnectionStatus) => void>();
  private connectListeners = new Set<() => void>();
  status: ConnectionStatus = 'idle';

  connect(playerId: string) {
    if (this.client && this.playerId === playerId && this.client.active) return;
    if (this.client) {
      this.client.deactivate();
      this.brokerSubs.clear();
    }
    this.playerId = playerId;
    this.setStatus('connecting');
    const client = new Client({
      webSocketFactory: () => new WebSocket(WS_URL),
      // the guest flow identifies over WS via the STOMP CONNECT header (GuestChannelInterceptor)
      connectHeaders: { 'X-Player-Id': playerId },
      reconnectDelay: 1000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 15000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.brokerSubs.clear();
        for (const dest of this.listeners.keys()) this.subscribeToBroker(dest);
        this.setStatus('connected');
        this.connectListeners.forEach((fn) => fn());
      },
      onWebSocketClose: () => {
        if (this.client === client && client.active) this.setStatus('reconnecting');
      },
      onStompError: (frame) => {
        console.warn('[ws] STOMP error:', frame.headers['message'] ?? frame.body);
      },
    });
    this.client = client;
    client.activate();
  }

  subscribe(dest: string, listener: Listener) {
    if (!this.listeners.has(dest)) this.listeners.set(dest, new Set());
    this.listeners.get(dest)!.add(listener);
    if (this.client?.connected) this.subscribeToBroker(dest);
    return () => this.unsubscribe(dest, listener);
  }

  publish(dest: string, payload?: unknown) {
    if (!this.client?.connected) return;
    this.client.publish({
      destination: dest,
      body: payload === undefined ? '' : JSON.stringify(payload),
      headers: { 'content-type': 'application/json' },
    });
  }

  /** Runs `fn` after every successful (re)connect. Returns an unsubscribe function. */
  onConnect(fn: () => void) {
    this.connectListeners.add(fn);
    return () => {
      this.connectListeners.delete(fn);
    };
  }

  onStatus(fn: (s: ConnectionStatus) => void) {
    this.statusListeners.add(fn);
    return () => {
      this.statusListeners.delete(fn);
    };
  }

  private setStatus(s: ConnectionStatus) {
    if (this.status === s) return;
    this.status = s;
    this.statusListeners.forEach((fn) => fn(s));
  }

  /** One broker subscription per destination, fanned out to all local listeners. */
  private subscribeToBroker(dest: string) {
    if (this.brokerSubs.has(dest) || !this.client?.connected) return;
    const sub = this.client.subscribe(dest, (msg: IMessage) => {
      const body = parse(msg.body);
      this.listeners.get(dest)?.forEach((fn) => fn(body));
    });
    this.brokerSubs.set(dest, sub);
  }

  private unsubscribe(dest: string, listener: Listener) {
    const set = this.listeners.get(dest);
    if (!set) return;
    set.delete(listener);
    if (set.size === 0) {
      this.listeners.delete(dest);
      const sub = this.brokerSubs.get(dest);
      this.brokerSubs.delete(dest);
      if (sub && this.client?.connected) sub.unsubscribe();
    }
  }

  disconnect() {
    this.client?.deactivate();
    this.client = null;
    this.playerId = null;
    this.brokerSubs.clear();
    this.setStatus('idle');
  }
}

function parse(body: string): unknown {
  try {
    return JSON.parse(body);
  } catch {
    return body;
  }
}

export const stomp = new StompConnection();

/** React hook: current socket status, re-rendering on change. */
export function useConnectionStatus(): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>(stomp.status);
  useEffect(() => stomp.onStatus(setStatus), []);
  return status;
}

import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type Listener = (payload: unknown) => void;

/**
 * Single shared STOMP connection (SockJS fallback transports included). Reconnects with
 * backoff via the underlying client; consumers just subscribe by destination.
 */
class StompConnection {
  private client: Client | null = null;
  private listeners = new Map<string, Set<Listener>>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  connect(playerId: string) {
    if (this.client) {
      this.disconnect();
    }
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws') as unknown as WebSocket,
      // the guest flow identifies over WS via the STOMP CONNECT header (GuestChannelInterceptor)
      connectHeaders: { 'X-Player-Id': playerId },
      reconnectDelay: 3000,
      onConnect: () => {
        for (const dest of this.listeners.keys()) {
          this.subscribeToBroker(dest);
        }
      },
    });
    this.client.activate();
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

  private subscribeToBroker(dest: string) {
    this.client!.subscribe(dest, (msg: IMessage) => {
      const body = parse(msg.body);
      this.listeners.get(dest)?.forEach((fn) => fn(body));
    });
  }

  private unsubscribe(dest: string, listener: Listener) {
    const set = this.listeners.get(dest);
    if (!set) return;
    set.delete(listener);
    if (set.size === 0) this.listeners.delete(dest);
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.client?.deactivate();
    this.client = null;
    this.listeners.clear();
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

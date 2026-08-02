import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { guestApi } from '../api';
import { ApiError } from '../api';
import { stomp } from '../ws';
import { loadSession, connectWS } from '../guest';
import Avatar from '../components/Avatar';
import SketchPad from '../components/SketchPad';
import type { GuestRoomInfo } from '../types';

export default function RoomPage() {
  const { id } = useParams();
  const roomId = id!;
  const navigate = useNavigate();
  const [session] = useState(() => loadSession());
  const [room, setRoom] = useState<GuestRoomInfo | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [wordInput, setWordInput] = useState('');
  const [wordError, setWordError] = useState('');

  useEffect(() => {
    if (!session) navigate('/', { replace: true });
    else connectWS();
  }, [session, navigate]);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      setRoom(await guestApi.getRoom(roomId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Room not found');
    }
  }, [roomId, session]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const unsub = stomp.subscribe(`/topic/room/${roomId}`, (payload) => {
      setRoom(payload as GuestRoomInfo);
    });
    const unsubGame = stomp.subscribe(`/topic/game/${roomId}`, (payload) => {
      if ((payload as { roundNumber?: number }).roundNumber !== undefined) {
        navigate(`/game/${roomId}`);
      }
    });
    return () => {
      unsub();
      unsubGame();
    };
  }, [roomId, navigate]);

  useEffect(() => {
    if (room?.status === 'PLAYING') navigate(`/game/${roomId}`);
  }, [room?.status, roomId, navigate]);

  const addWords = async (e: FormEvent) => {
    e.preventDefault();
    if (!room || !session) return;
    setWordError('');
    const list = wordInput
      .split(/[,;\n]/)
      .map((w) => w.trim())
      .filter((w) => w.length > 0 && w.length <= 64);
    if (list.length === 0) return;
    try {
      setRoom(await guestApi.addWords(session.playerId, room.roomId, list));
      setWordInput('');
    } catch (err) {
      setWordError(err instanceof ApiError ? err.message : 'Could not add words');
    }
  };

  const removeWord = async (word: string) => {
    if (!room || !session) return;
    try {
      setRoom(await guestApi.removeWord(session.playerId, room.roomId, word));
    } catch (err) {
      setWordError(err instanceof ApiError ? err.message : 'Could not remove word');
    }
  };

  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError('');
    try {
      await fn();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  };

  const leave = () =>
    act(async () => {
      if (session) await guestApi.leaveRoom(session.playerId, roomId);
      navigate('/');
    });

  if (!session) return null;
  if (error && !room) return <p className="text-red-500">{error}</p>;
  if (!room) return <p className="text-slate-400">Loading room…</p>;

  const me = room.players.find((p) => p.playerId === session.playerId);
  const amHost = room.hostId === session.playerId;

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6 grid gap-6 lg:grid-cols-[1fr_320px]">
      <section>
        <h1 className="text-xl font-bold text-slate-700">
          {room.name}
          <span className="ml-3 rounded-full bg-slate-200 px-2 py-0.5 text-sm font-semibold text-slate-600">
            {room.isPrivate ? 'PRIVATE' : 'PUBLIC'}
          </span>
          {room.status === 'PLAYING' && <span className="ml-2 text-sm text-green-500">in game</span>}
        </h1>
        <p className="mt-1 text-sm text-slate-400">
          {room.totalRounds} rounds · {room.drawingTimeSec}s drawing · share code{' '}
          <span className="font-mono font-semibold text-[#6d5dfc]">{room.roomId}</span>
        </p>

        {error && <p className="mt-3 rounded bg-red-50 px-3 py-2 text-sm text-red-500">{error}</p>}

        <ul className="mt-4 space-y-2">
          {room.players.map((p) => (
            <li key={p.playerId} className="flex items-center justify-between rounded-xl border-2 border-amber-100 bg-white px-4 py-3 shadow-sm shadow-amber-50">
              <div className="flex items-center gap-3">
                <Avatar avatar={p} size={36} />
                <div>
                  <p className="font-semibold text-slate-700">
                    {p.nickname}
                    {p.isHost && <span className="ml-2 text-xs text-amber-500">HOST</span>}
                    {p.playerId === session.playerId && <span className="ml-2 text-xs text-[#6d5dfc]">you</span>}
                  </p>
                  <p className="text-xs text-slate-400">{p.isConnected ? 'connected' : 'away'}</p>
                </div>
              </div>
              <span className="font-semibold text-slate-500">{p.score} pts</span>
            </li>
          ))}
        </ul>

        {room.customWordsEnabled && (
          <div className="mt-5 rounded-2xl border-2 border-amber-100 bg-white p-4 shadow-sm shadow-amber-50">
            <h2 className="font-semibold text-slate-700">Custom words</h2>
            <p className="mt-1 text-xs text-slate-400">
              Words picked from here instead of the pool. Need at least 3 to start.
            </p>
            {amHost ? (
              <form onSubmit={addWords} className="mt-3 flex gap-2">
                <input
                  className="input !py-1 text-sm"
                  placeholder="apple, banana, cherry…"
                  value={wordInput}
                  onChange={(e) => setWordInput(e.target.value)}
                />
                <button className="btn !py-1 text-sm" type="submit">
                  Add
                </button>
              </form>
            ) : (
              <p className="mt-3 text-xs text-slate-500">The host manages the word list.</p>
            )}
            {wordError && <p className="mt-2 text-xs text-red-500">{wordError}</p>}
            <ul className="mt-3 flex flex-wrap gap-2">
              {room.customWords.map((w) => (
                <li key={w} className="flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-700">
                  {w}
                  {amHost && (
                    <button className="text-slate-400 hover:text-red-500" onClick={() => removeWord(w)} aria-label={`remove ${w}`}>
                      ×
                    </button>
                  )}
                </li>
              ))}
              {room.customWords.length === 0 && <li className="text-xs text-slate-500">No words yet.</li>}
            </ul>
          </div>
        )}
      </section>

      <aside className="space-y-3">
        {amHost && (
          <button className="btn w-full" disabled={busy || room.players.length < 2} onClick={() => act(() => guestApi.startGame(session.playerId, room.roomId))}>
            Start game
          </button>
        )}
        <button className="btn-ghost w-full" disabled={busy} onClick={leave}>
          Leave room
        </button>
        <p className="text-xs text-slate-500">
          {me?.isConnected ? 'You are connected.' : 'Reconnect to rejoin.'}
          {' '}{room.players.length >= 2 ? 'All set — host can start.' : 'Need at least 2 players to start.'}
        </p>
      </aside>

      <div className="lg:col-span-2 space-y-2">
        <p className="text-center text-sm font-semibold text-slate-500">✏️ Sketch while you wait</p>
        <SketchPad height={400} />
        <p className="text-center text-xs text-slate-400">Your doodles are private — not broadcast.</p>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { guestApi } from '../api';
import { ApiError } from '../api';
import { stomp, useConnectionStatus } from '../ws';
import { loadSession, connectWS } from '../guest';
import Avatar from '../components/Avatar';
import Canvas from '../components/Canvas';
import { Bot, Check, Clock, Copy, Crown, Globe, LogOut, Minus, Play, Plus, Repeat, Settings2, UserX, X } from 'lucide-react';
import type { GuestRoomInfo, RoomSettings } from '../types';
import { LANGUAGES } from '../languages';

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
  const [copied, setCopied] = useState(false);
  const [notice, setNotice] = useState('');
  const wsStatus = useConnectionStatus();

  useEffect(() => {
    // opened an invite link without a session: pick a nickname first, then auto-join
    if (!session) navigate(`/?join=${encodeURIComponent(roomId)}`, { replace: true });
    else connectWS();
  }, [session, navigate]);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      let info = await guestApi.getRoom(roomId);
      // arrived via invite link / open-games list but not in this room yet → join it (even mid-game)
      if (!info.players.some((p) => p.playerId === session.playerId) && info.status !== 'FINISHED') {
        info = await guestApi.joinRoom(session.playerId, roomId);
      }
      setRoom(info);
      setError('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Room not found');
    }
  }, [roomId, session]);

  useEffect(() => {
    load();
  }, [load]);

  // after every (re)connect, refetch: presence/joins broadcast while offline were missed
  useEffect(() => stomp.onConnect(() => void load()), [load]);

  const inviteLink = `${window.location.origin}/rooms/${roomId}`;
  const copyInvite = async () => {
    try {
      await navigator.clipboard.writeText(inviteLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      window.prompt('Copy this invite link:', inviteLink);
    }
  };

  useEffect(() => {
    const unsub = stomp.subscribe(`/topic/room/${roomId}`, (payload) => {
      setRoom(payload as GuestRoomInfo);
    });
    const unsubGame = stomp.subscribe(`/topic/game/${roomId}`, (payload) => {
      const msg = payload as { roundNumber?: number; system?: string };
      if (msg.roundNumber !== undefined) {
        navigate(`/game/${roomId}`);
      } else if (msg.system) {
        setNotice(msg.system);
      }
    });
    const unsubKick = stomp.subscribe('/user/queue/kicked', () => navigate('/?kicked=1', { replace: true }));
    return () => {
      unsub();
      unsubGame();
      unsubKick();
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

  const saveSettings = (patch: RoomSettings) =>
    act(async () => {
      if (session) setRoom(await guestApi.updateSettings(session.playerId, roomId, patch));
    });

  const leave = () =>
    act(async () => {
      if (session) await guestApi.leaveRoom(session.playerId, roomId);
      navigate('/');
    });

  const kick = (pid: string, name: string) => {
    if (!session || !window.confirm(`Vote to kick ${name}?`)) return;
    act(() => guestApi.voteKick(session.playerId, roomId, pid));
  };

  if (!session) return null;
  if (error && !room)
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-4xl">Room not found</h1>
        <p className="mt-2 text-ink-soft">{error}</p>
        <button className="btn mt-6" onClick={() => navigate('/')}>
          Back to the lobby
        </button>
      </div>
    );
  if (!room) return <p className="py-20 text-center font-hand text-2xl text-ink-faint">Loading room…</p>;

  const amHost = room.hostId === session.playerId;
  const host = room.players.find((p) => p.playerId === room.hostId);
  const enough = room.players.length >= 2;
  const emptySeats = Math.max(0, Math.min(room.maxPlayers, 8) - room.players.length);

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-8 sm:py-10">
      {/* header */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="font-hand text-lg text-ink-faint">{room.isPrivate ? 'Private room' : 'Public room'}</p>
          <h1 className="text-4xl leading-none sm:text-5xl">{room.name}</h1>
          <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm font-semibold text-ink-soft">
            <span className="inline-flex items-center gap-1">
              <Repeat size={15} /> {room.totalRounds} rounds
            </span>
            <span className="inline-flex items-center gap-1">
              <Clock size={15} /> {room.drawingTimeSec}s to draw
            </span>
            <span className="inline-flex items-center gap-1">
              <Globe size={15} /> {LANGUAGES.find((l) => l.code === room.language)?.label ?? 'English'} words
            </span>
          </p>
        </div>
        <button
          className="group flex items-center gap-3 rounded-xl border-2 border-dashed border-ink bg-white px-3 py-2 text-left transition hover:bg-marker-soft"
          onClick={copyInvite}
          title="Copy invite link"
        >
          <span>
            <span className="block text-xs font-semibold text-ink-faint">Room code</span>
            <span className="font-mono text-xl font-bold tracking-widest">{room.roomId}</span>
          </span>
          {copied ? <Check size={20} className="text-go" /> : <Copy size={20} />}
        </button>
      </div>

      {error && (
        <p role="alert" className="mt-4 rounded-xl border-2 border-stop bg-white px-3 py-2 text-sm font-semibold text-stop">
          {error}
        </p>
      )}
      {notice && <p className="mt-4 rounded-xl border-2 border-dashed border-ink-faint bg-white px-3 py-2 text-sm italic text-ink-soft">{notice}</p>}

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px] lg:items-start">
        {/* players */}
        <section className="sheet p-4 sm:p-5">
          <div className="mb-3 flex items-baseline justify-between">
            <h2 className="text-3xl leading-none">Players</h2>
            <span className="font-hand text-lg text-ink-faint">
              {room.players.length} / {room.maxPlayers}
            </span>
          </div>
          <ul className="grid gap-3 sm:grid-cols-2">
            {room.players.map((p) => {
              const me = p.playerId === session.playerId;
              const online = me ? wsStatus === 'connected' : p.isConnected;
              return (
                <li
                  key={p.playerId}
                  className={`flex items-center gap-3 rounded-xl border-2 px-3 py-2.5 ${me ? 'border-ink bg-marker-soft' : 'border-ink/20 bg-white'}`}
                >
                  <div className="relative">
                    <Avatar avatar={p} size={44} />
                    <span
                      className={`absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-white ${online ? 'bg-go' : 'bg-ink-faint'}`}
                      title={online ? 'online' : 'away'}
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-1.5 truncate font-bold">
                      <span className="truncate">{p.nickname}</span>
                      {p.isHost && <Crown size={15} className="shrink-0 text-marker-deep" fill="currentColor" aria-label="host" />}
                    </p>
                    <p className="flex flex-wrap gap-1 pt-0.5">
                      {me && <span className="tag bg-marker">you</span>}
                      {p.isBot && (
                        <span className="tag">
                          <Bot size={12} /> bot
                        </span>
                      )}
                      {!online && !p.isBot && <span className="tag border-dashed text-ink-faint">away</span>}
                    </p>
                  </div>
                  {p.isBot && amHost && (
                    <button
                      className="btn-quiet !p-1.5 hover:!text-stop"
                      onClick={() => act(() => guestApi.removeBot(session.playerId, roomId, p.playerId))}
                      aria-label={`Remove ${p.nickname}`}
                      title="Remove bot"
                    >
                      <X size={18} />
                    </button>
                  )}
                  {!p.isBot && !me && (
                    <button
                      className="btn-quiet !p-1.5 hover:!text-stop"
                      onClick={() => kick(p.playerId, p.nickname)}
                      aria-label={`${amHost ? 'Kick' : 'Vote to kick'} ${p.nickname}`}
                      title={amHost ? 'Kick' : 'Vote to kick'}
                    >
                      <UserX size={18} />
                    </button>
                  )}
                </li>
              );
            })}
            {Array.from({ length: emptySeats }, (_, i) => (
              <li
                key={`empty-${i}`}
                className="hidden items-center justify-center rounded-xl border-2 border-dashed border-ink/15 px-3 py-2.5 font-hand text-lg text-ink-faint sm:flex"
              >
                empty seat
              </li>
            ))}
          </ul>
        </section>

        <div className="space-y-6 lg:sticky lg:top-6">
        {/* actions */}
        <aside className="sheet space-y-3 p-4 sm:p-5">
          {amHost ? (
            <>
              <button
                className="btn btn-lg w-full"
                disabled={busy || !enough || wsStatus !== 'connected'}
                onClick={() => act(() => guestApi.startGame(session.playerId, room.roomId))}
              >
                <Play size={22} fill="currentColor" /> Start game
              </button>
              <p className="text-center text-sm text-ink-soft">
                {wsStatus !== 'connected'
                  ? 'Connecting to the game server…'
                  : enough
                    ? 'Everyone in? Hit start.'
                    : 'You need at least 2 players. Invite a friend or add a bot.'}
              </p>
            </>
          ) : (
            <div className="rounded-xl border-2 border-dashed border-ink/30 px-3 py-4 text-center">
              <p className="font-hand text-2xl leading-none">Waiting for the host…</p>
              <p className="mt-1 text-sm text-ink-soft">{host?.nickname ?? 'The host'} will start the game.</p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-2 lg:grid-cols-1">
            <button className="btn-secondary !text-lg" onClick={copyInvite}>
              {copied ? <Check size={18} /> : <Copy size={18} />}
              {copied ? 'Link copied' : 'Invite link'}
            </button>
            {amHost && room.status === 'WAITING' && (
              <button
                className="btn-secondary !text-lg"
                disabled={busy || room.players.length >= room.maxPlayers}
                onClick={() => act(() => guestApi.addBot(session.playerId, roomId))}
                title="Bots guess your drawings — they don't draw"
              >
                <Bot size={18} /> Add a bot
              </button>
            )}
          </div>
          {room.players.some((p) => p.isBot) && <p className="text-center text-xs text-ink-faint">Bots guess but never draw.</p>}

          <button className="btn-quiet w-full justify-center" disabled={busy} onClick={leave}>
            <LogOut size={16} /> Leave room
          </button>
        </aside>

        <SettingsPanel room={room} editable={amHost && room.status !== 'PLAYING'} busy={busy} onChange={saveSettings} />
        </div>
      </div>

      {room.customWordsEnabled && (
        <section className="sheet mt-6 p-4 sm:p-5">
          <h2 className="text-3xl leading-none">Custom words</h2>
          <p className="mt-1 text-sm text-ink-soft">Words are picked from this list instead of the pool. Add at least 3.</p>
          {amHost ? (
            <form onSubmit={addWords} className="mt-3 flex gap-2">
              <input
                className="input"
                placeholder="apple, banana, cherry…"
                value={wordInput}
                onChange={(e) => setWordInput(e.target.value)}
              />
              <button className="btn-secondary !text-lg" type="submit">
                Add
              </button>
            </form>
          ) : (
            <p className="mt-3 text-sm text-ink-soft">The host manages the word list.</p>
          )}
          {wordError && <p className="mt-2 text-sm font-semibold text-stop">{wordError}</p>}
          <ul className="mt-3 flex flex-wrap gap-2">
            {room.customWords.map((w) => (
              <li key={w} className="tag !text-base">
                {w}
                {amHost && (
                  <button className="text-ink-faint hover:text-stop" onClick={() => removeWord(w)} aria-label={`remove ${w}`}>
                    <X size={14} />
                  </button>
                )}
              </li>
            ))}
            {room.customWords.length === 0 && <li className="text-sm text-ink-faint">No words yet.</li>}
          </ul>
        </section>
      )}

      <section className="mt-8 space-y-2">
        <div className="flex items-baseline justify-between px-1">
          <h2 className="text-3xl">Doodle while you wait</h2>
          <span className="text-sm text-ink-faint">only you can see this</span>
        </div>
        <Canvas height={360} />
      </section>
    </div>
  );
}

const DRAW_TIMES = [45, 60, 80, 100, 120, 180];

/** Room settings: editable controls for the host, a read-only summary for everyone else. */
function SettingsPanel({
  room,
  editable,
  busy,
  onChange,
}: {
  room: GuestRoomInfo;
  editable: boolean;
  busy: boolean;
  onChange: (patch: RoomSettings) => void;
}) {
  const disabled = !editable || busy;
  return (
    <section className="sheet p-4 sm:p-5">
      <h2 className="flex items-center gap-2 text-3xl leading-none">
        <Settings2 size={22} /> Settings
      </h2>
      {!editable && <p className="mt-1 text-xs text-ink-faint">Only the host can change these.</p>}
      <dl className="mt-4 space-y-3.5">
        <Row label="Rounds">
          <NumberStepper value={room.totalRounds} min={1} max={10} disabled={disabled} onChange={(v) => onChange({ totalRounds: v })} />
        </Row>
        <Row label="Players">
          <NumberStepper
            value={room.maxPlayers}
            min={Math.max(2, room.players.length)}
            max={12}
            disabled={disabled}
            onChange={(v) => onChange({ maxPlayers: v })}
          />
        </Row>
        <div>
          <dt className="label">Draw time</dt>
          <dd className="mt-1.5 flex flex-wrap gap-1.5">
            {DRAW_TIMES.map((t) => (
              <button
                key={t}
                disabled={disabled}
                onClick={() => onChange({ drawingTimeSec: t })}
                aria-pressed={room.drawingTimeSec === t}
                className={`rounded-lg border-2 px-2 py-0.5 font-hand text-lg leading-tight transition disabled:cursor-default ${
                  room.drawingTimeSec === t ? 'border-ink bg-marker' : 'border-ink/20 enabled:hover:border-ink'
                }`}
              >
                {t}s
              </button>
            ))}
          </dd>
        </div>
        <Row label="Words in">
          <select
            className="rounded-lg border-2 border-ink/25 bg-white px-2 py-1 text-sm font-semibold enabled:hover:border-ink disabled:opacity-70"
            value={room.language}
            disabled={disabled}
            onChange={(e) => onChange({ language: e.target.value })}
          >
            {LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.label}
              </option>
            ))}
          </select>
        </Row>
        <Row label="Letter hints" hint="Reveal letters as time runs out">
          <Toggle on={room.hintsEnabled} disabled={disabled} onChange={(v) => onChange({ hints: v })} label="Letter hints" />
        </Row>
        <Row label="Custom words" hint="Only use your own word list">
          <Toggle on={room.customWordsEnabled} disabled={disabled} onChange={(v) => onChange({ customWords: v })} label="Custom words" />
        </Row>
        <Row label="Public" hint="Strangers can join with Play">
          <Toggle on={!room.isPrivate} disabled={disabled} onChange={(v) => onChange({ isPrivate: !v })} label="Public room" />
        </Row>
      </dl>
    </section>
  );
}

function Row({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt>
        <span className="label !mb-0">{label}</span>
        {hint && <span className="block text-xs text-ink-faint">{hint}</span>}
      </dt>
      <dd>{children}</dd>
    </div>
  );
}

function NumberStepper({
  value,
  min,
  max,
  disabled,
  onChange,
}: {
  value: number;
  min: number;
  max: number;
  disabled: boolean;
  onChange: (v: number) => void;
}) {
  return (
    <div className="flex items-center gap-1">
      <button className="icon-btn !h-7 !w-7" disabled={disabled || value <= min} onClick={() => onChange(value - 1)} aria-label="Decrease">
        <Minus size={14} />
      </button>
      <span className="w-8 text-center font-hand text-2xl leading-none">{value}</span>
      <button className="icon-btn !h-7 !w-7" disabled={disabled || value >= max} onClick={() => onChange(value + 1)} aria-label="Increase">
        <Plus size={14} />
      </button>
    </div>
  );
}

function Toggle({ on, disabled, onChange, label }: { on: boolean; disabled: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!on)}
      className={`relative h-7 w-12 shrink-0 rounded-full border-2 border-ink transition disabled:cursor-default disabled:opacity-70 ${on ? 'bg-marker' : 'bg-paper-deep'}`}
    >
      <span className={`absolute top-0.5 h-5 w-5 rounded-full border-2 border-ink bg-white transition-all ${on ? 'left-[22px]' : 'left-0.5'}`} />
    </button>
  );
}

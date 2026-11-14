import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import * as React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Bot,
  Clock,
  Globe,
  Users,
  ChevronLeft,
  ChevronRight,
  Dices,
  Glasses,
  Lock,
  MessageCircle,
  Pencil,
  Play,
  Trophy,
} from 'lucide-react';
import { guestApi, pingBackend } from '../api';
import { ApiError } from '../api';
import { createGuestSession, resumeSession } from '../guest';
import type { GuestSession } from '../guest';
import type { GuestRoomInfo } from '../types';
import Avatar from '../components/Avatar';
import Doodle from '../components/Doodle';
import Canvas from '../components/Canvas';
import { LANGUAGES } from '../languages';

const COLORS = ['#ffd23f', '#ff8a5b', '#ff6b6b', '#f783ac', '#b197fc', '#74c0fc', '#63e6be', '#a9e34b', '#e9ecef'];
const EXPRESSIONS = ['happy', 'sad', 'angry', 'surprised', 'wink'];
const WIGS = ['none', 'curly', 'bob', 'spiky'];
const DOODLE_INKS = ['#1f1d1a', '#1f1d1a', '#8a847b', '#f5b700'];

export default function LobbyPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const inviteCode = params.get('join')?.trim() ?? '';
  const wasKicked = params.get('kicked') === '1';
  const [session, setSession] = useState<GuestSession | null>(null);
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState({
    avatarColor: '#ffd23f',
    avatarExpression: 'happy',
    avatarSunglasses: false,
    avatarWig: 'none',
  });
  const [language, setLanguage] = useState('en');
  const [roomCode, setRoomCode] = useState(inviteCode);
  const [error, setError] = useState(wasKicked ? 'You were removed from that room.' : '');
  const [busy, setBusy] = useState(false);
  // 'checking' → first ping in flight; 'waking' → slow (free-tier server is cold-starting)
  const [server, setServer] = useState<'checking' | 'waking' | 'ready' | 'down'>('checking');
  const [openRooms, setOpenRooms] = useState<GuestRoomInfo[] | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);

  // public rooms anyone can hop into; refreshed every few seconds while the tab is visible
  useEffect(() => {
    if (server !== 'ready') return;
    let alive = true;
    const refresh = () => {
      if (document.visibilityState !== 'visible') return;
      guestApi
        .listRooms()
        .then((rooms) => alive && setOpenRooms(rooms))
        .catch(() => {});
    };
    refresh();
    const t = setInterval(refresh, 5000);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      alive = false;
      clearInterval(t);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [server]);

  // Wake the backend as soon as the page opens, so it's warm by the time the player
  // has picked a nickname. If it's slow, tell them why instead of looking broken.
  useEffect(() => {
    let done = false;
    const slow = setTimeout(() => !done && setServer('waking'), 1500);
    pingBackend().then((ok) => {
      done = true;
      clearTimeout(slow);
      setServer(ok ? 'ready' : 'down');
    });
    return () => clearTimeout(slow);
  }, []);

  // restore a stored session (refresh) — jump back into the room if still live
  useEffect(() => {
    resumeSession().then((r) => {
      if (!r) return;
      setSession(r.session);
      setNickname(r.session.nickname);
      setAvatar({
        avatarColor: r.session.avatarColor,
        avatarExpression: r.session.avatarExpression,
        avatarSunglasses: r.session.avatarSunglasses,
        avatarWig: r.session.avatarWig,
      });
      setLanguage(r.session.language);
      if (r.roomId) navigate(`/rooms/${r.roomId}`, { replace: true });
    });
  }, [navigate]);

  const ensureSession = useCallback(async (): Promise<GuestSession> => {
    if (session && session.nickname === nickname) return session;
    return createGuestSession({ nickname, ...avatar, language });
  }, [session, nickname, avatar, language]);

  const enterRoom = async (isPrivate: boolean) => {
    setError('');
    setBusy(true);
    try {
      const s = await ensureSession();
      const room = await guestApi.createRoom(s.playerId, {
        name: `${nickname}'s room`,
        isPrivate,
        maxPlayers: 8,
        totalRounds: 3,
        drawingTimeSec: 80,
        customWords: false,
        language,
      });
      setSession(s);
      navigate(`/rooms/${room.roomId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create a room');
    } finally {
      setBusy(false);
    }
  };

  /** Solo practice: a private room with two bots that guess while you draw. */
  const practice = async () => {
    setError('');
    setBusy(true);
    try {
      const s = await ensureSession();
      const room = await guestApi.createRoom(s.playerId, {
        name: `${nickname}'s practice`,
        isPrivate: true,
        maxPlayers: 8,
        totalRounds: 3,
        drawingTimeSec: 80,
        customWords: false,
        language,
      });
      await guestApi.addBot(s.playerId, room.roomId);
      await guestApi.addBot(s.playerId, room.roomId);
      setSession(s);
      navigate(`/rooms/${room.roomId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start practice');
    } finally {
      setBusy(false);
    }
  };

  /** Public play: join an open public room if there is one, otherwise host a new one. */
  const onPlay = async (e: FormEvent) => {
    e.preventDefault();
    if (inviteCode) return joinByCode();
    setError('');
    setBusy(true);
    try {
      const s = await ensureSession();
      const open = (await guestApi.listRooms())
        // server lists waiting rooms first, then games already under way
        .filter((r) => !r.isPrivate && r.status !== 'FINISHED' && r.playerCount < r.maxPlayers);
      for (const r of open) {
        try {
          const room = await guestApi.joinRoom(s.playerId, r.roomId);
          setSession(s);
          navigate(`/rooms/${room.roomId}`);
          return;
        } catch {
          /* filled up or started in the meantime — try the next one */
        }
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach the game server');
      setBusy(false);
      return;
    }
    setBusy(false);
    enterRoom(false);
  };

  const joinByCode = () => joinRoomId(roomCode.trim());

  /** Join a room by id (code box or the open-games list). Needs a name first. */
  const joinRoomId = async (code: string) => {
    if (!code) return;
    if (!nickname.trim()) {
      setError('Pick a name first, then join.');
      nameRef.current?.focus();
      nameRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    setError('');
    setBusy(true);
    try {
      const s = await ensureSession();
      const room = await guestApi.joinRoom(s.playerId, code);
      setSession(s);
      navigate(`/rooms/${room.roomId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not join room');
    } finally {
      setBusy(false);
    }
  };

  const cycle = <T,>(list: T[], current: T, dir: 1 | -1) =>
    list[(list.indexOf(current) + dir + list.length) % list.length];
  const randomize = () =>
    setAvatar({
      avatarColor: COLORS[Math.floor(Math.random() * COLORS.length)],
      avatarExpression: EXPRESSIONS[Math.floor(Math.random() * EXPRESSIONS.length)],
      avatarWig: WIGS[Math.floor(Math.random() * WIGS.length)],
      avatarSunglasses: Math.random() < 0.25,
    });
  const canGo = !busy && nickname.trim().length > 0;

  return (
    <div className="mx-auto w-full max-w-6xl px-4 pb-10 sm:px-8">
      {/* HERO */}
      <header className="relative py-10 text-center sm:py-14">
        <DoodleScatter count={8} minSize={26} maxSize={56} />
        <div className="relative z-10">
          <h1 className="text-6xl leading-none sm:text-8xl">
            <span className="highlight">Sketch</span>Trench
          </h1>
          <p className="mt-3 font-hand text-2xl text-ink-soft sm:text-3xl">Draw it. Guess it. Win it.</p>
          <p className="mt-2 text-sm font-semibold text-ink-faint">Free · no sign-up · play with friends or bots</p>
        </div>
      </header>

      <section className="grid gap-8 lg:grid-cols-[minmax(0,430px)_minmax(0,1fr)] lg:items-start">
        {/* JOIN CARD */}
        <form onSubmit={onPlay} className="sheet space-y-5 p-5 sm:p-6 lg:sticky lg:top-6">
          {inviteCode && (
            <p className="rounded-xl border-2 border-ink bg-marker-soft px-3 py-2 text-center text-sm font-semibold">
              You're invited to room <span className="font-mono">{inviteCode}</span>. Pick a name and jump in!
            </p>
          )}
          {server === 'waking' && (
            <p role="status" className="flex items-center gap-2 rounded-xl border-2 border-dashed border-ink-faint px-3 py-2 text-sm text-ink-soft">
              <span className="h-3 w-3 shrink-0 animate-spin rounded-full border-2 border-ink border-t-transparent" />
              Waking up the game server — the first visit can take up to a minute.
            </p>
          )}
          {server === 'down' && (
            <p role="alert" className="rounded-xl border-2 border-stop px-3 py-2 text-sm font-semibold text-stop">
              Can't reach the game server right now. Please try again in a minute.
            </p>
          )}
          {error && (
            <p role="alert" className="rounded-xl border-2 border-stop px-3 py-2 text-sm font-semibold text-stop">
              {error}
            </p>
          )}

          {/* avatar builder */}
          <div className="flex items-center gap-4">
            <div className="relative shrink-0">
              <Avatar avatar={avatar} size={104} />
              <button
                type="button"
                onClick={randomize}
                className="icon-btn absolute -bottom-1 -right-1 !h-8 !w-8 shadow-sketch-sm"
                aria-label="Random avatar"
                title="Random avatar"
              >
                <Dices size={16} />
              </button>
            </div>
            <div className="flex-1 space-y-1.5">
              <Stepper
                label="Face"
                value={avatar.avatarExpression}
                onPrev={() => setAvatar((a) => ({ ...a, avatarExpression: cycle(EXPRESSIONS, a.avatarExpression, -1) }))}
                onNext={() => setAvatar((a) => ({ ...a, avatarExpression: cycle(EXPRESSIONS, a.avatarExpression, 1) }))}
              />
              <Stepper
                label="Hair"
                value={avatar.avatarWig}
                onPrev={() => setAvatar((a) => ({ ...a, avatarWig: cycle(WIGS, a.avatarWig, -1) }))}
                onNext={() => setAvatar((a) => ({ ...a, avatarWig: cycle(WIGS, a.avatarWig, 1) }))}
              />
              <button
                type="button"
                aria-pressed={avatar.avatarSunglasses}
                onClick={() => setAvatar((a) => ({ ...a, avatarSunglasses: !a.avatarSunglasses }))}
                className={`flex w-full items-center justify-between rounded-lg border-2 px-2 py-1 font-hand text-lg leading-none transition ${
                  avatar.avatarSunglasses ? 'border-ink bg-marker' : 'border-ink/25 hover:border-ink'
                }`}
              >
                <span>Shades</span>
                <Glasses size={18} />
              </button>
            </div>
          </div>
          <div className="grid grid-cols-9 justify-items-center gap-1" role="group" aria-label="Avatar colour">
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setAvatar((a) => ({ ...a, avatarColor: c }))}
                className={`h-7 w-7 rounded-full border-2 border-ink transition ${
                  avatar.avatarColor === c ? 'scale-110 shadow-sketch-sm ring-2 ring-ink ring-offset-2' : 'hover:scale-110'
                }`}
                style={{ backgroundColor: c }}
                aria-label={`colour ${c}`}
                aria-pressed={avatar.avatarColor === c}
              />
            ))}
          </div>

          <label className="block">
            <span className="label">Your name</span>
            <input
              className="input text-lg font-semibold"
              placeholder="e.g. Picasso"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
              minLength={1}
              maxLength={20}
              autoComplete="nickname"
              ref={nameRef}
            />
          </label>

          <div className="space-y-3">
            <button type="submit" disabled={!canGo} className="btn btn-lg w-full">
              <Play size={22} fill="currentColor" />
              {busy ? (server === 'ready' ? 'Joining…' : 'Waking server…') : inviteCode ? 'Join room' : 'Play'}
            </button>
            <div className="grid grid-cols-2 gap-3">
              <button type="button" disabled={!canGo} onClick={() => enterRoom(true)} className="btn-secondary !text-lg">
                <Lock size={17} />
                <span className="whitespace-nowrap">
                  Private<span className="hidden min-[400px]:inline"> room</span>
                </span>
              </button>
              <button type="button" disabled={!canGo} onClick={practice} className="btn-secondary !text-lg" title="You draw, bots guess">
                <Bot size={18} />
                Practice
              </button>
            </div>
          </div>

          <div className="flex items-center gap-3 text-sm font-semibold text-ink-faint">
            <span className="h-0.5 flex-1 rounded bg-paper-line" />
            have a room code?
            <span className="h-0.5 flex-1 rounded bg-paper-line" />
          </div>
          <div className="flex gap-2">
            <input
              className="input text-center font-mono tracking-widest"
              placeholder="abc12345"
              aria-label="Room code"
              value={roomCode}
              onChange={(e) => setRoomCode(e.target.value)}
              maxLength={8}
            />
            <button type="button" disabled={busy || !roomCode.trim()} onClick={joinByCode} className="btn-secondary !text-lg">
              Join
            </button>
          </div>

          <div className="flex items-center justify-between gap-3 border-t-2 border-dashed border-paper-line pt-4">
            <label className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
              Words in
              <select
                className="rounded-lg border-2 border-ink/25 bg-white px-2 py-1 text-sm text-ink hover:border-ink"
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
              >
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.label}
                  </option>
                ))}
              </select>
            </label>
            <span className="text-right text-xs text-ink-faint">No account — you stay in this browser.</span>
          </div>
        </form>

        {/* WARM-UP PAD */}
        <div className="space-y-2">
          <div className="flex items-baseline justify-between px-1">
            <h2 className="text-3xl">Warm-up pad</h2>
            <span className="text-sm text-ink-faint">only you can see this</span>
          </div>
          <Canvas height={480} />
        </div>
      </section>

      {/* OPEN GAMES */}
      {server === 'ready' && (
        <OpenGames rooms={openRooms} busy={busy} onJoin={(id) => joinRoomId(id)} />
      )}

      {/* HOW TO PLAY */}
      <section className="mt-16">
        <h2 className="text-center text-4xl">
          How to <span className="highlight">play</span>
        </h2>
        <ol className="mt-8 grid gap-5 sm:grid-cols-3">
          {(
            [
              [Pencil, 'Draw', 'Pick one of three words and sketch it. No letters, no numbers — just doodles.'],
              [MessageCircle, 'Guess', 'Everyone else types guesses in the chat. Faster guesses earn more points.'],
              [Trophy, 'Win', 'The artist scores for every correct guess. Most points after all rounds wins.'],
            ] as const
          ).map(([Icon, title, text], i) => (
            <li key={title} className="sheet-flat relative p-5 pt-6 animate-step-in" style={{ animationDelay: `${i * 0.08}s` }}>
              <span className="absolute -top-4 left-4 flex h-9 w-9 items-center justify-center rounded-full border-2 border-ink bg-marker font-hand text-xl">
                {i + 1}
              </span>
              <Icon size={28} strokeWidth={1.75} />
              <h3 className="mt-2 text-2xl leading-none">{title}</h3>
              <p className="mt-2 text-sm text-ink-soft">{text}</p>
            </li>
          ))}
        </ol>
        <p className="mt-6 text-center text-sm text-ink-soft">
          Playing with friends? Make a private room and share the link. Nobody around? Practice with bots — you draw,
          they guess.
        </p>
      </section>

      <footer className="mt-14 border-t-2 border-dashed border-paper-line pt-5 text-center text-sm text-ink-faint">
        Built by Shashwat Jha ·{' '}
        <a
          className="font-semibold text-ink underline decoration-marker-deep decoration-2 underline-offset-2 hover:bg-marker-soft"
          href="https://github.com/shashwatjha17/sketchTrench_game"
          target="_blank"
          rel="noreferrer"
        >
          Source on GitHub
        </a>
      </footer>
    </div>
  );
}

function Stepper({
  label,
  value,
  onPrev,
  onNext,
}: {
  label: string;
  value: string;
  onPrev: () => void;
  onNext: () => void;
}) {
  return (
    <div className="flex items-center justify-between gap-1 font-hand text-lg leading-none">
      <span className="w-11 text-ink-soft">{label}</span>
      <button type="button" className="btn-quiet !p-1" onClick={onPrev} aria-label={`Previous ${label.toLowerCase()}`}>
        <ChevronLeft size={18} />
      </button>
      <span className="flex-1 text-center capitalize">{value}</span>
      <button type="button" className="btn-quiet !p-1" onClick={onNext} aria-label={`Next ${label.toLowerCase()}`}>
        <ChevronRight size={18} />
      </button>
    </div>
  );
}

/** Scatters hand-drawn doodles across a section, seeded once per mount. */
function DoodleScatter({ count, minSize, maxSize }: { count: number; minSize: number; maxSize: number }) {
  const doodles = useMemo(() => {
    let seed = 0x2f6e2b1;
    const rnd = () => {
      seed = (seed * 1664525 + 1013904223) >>> 0;
      return seed / 0xffffffff;
    };
    // keep doodles near the edges so they never sit on top of the title / tagline
    const edgePos = () => {
      let left = 0;
      let top = 0;
      do {
        left = rnd() * 94;
        top = rnd() * 82;
      } while (left > 7 && left < 86);
      return { left, top };
    };
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      size: minSize + rnd() * (maxSize - minSize),
      ...edgePos(),
      rotate: (rnd() - 0.5) * 28,
      delay: rnd() * 2,
      duration: 1.2 + rnd() * 2,
      color: DOODLE_INKS[Math.floor(rnd() * DOODLE_INKS.length)],
    }));
  }, [count, minSize, maxSize]);

  return (
    <div aria-hidden className="absolute inset-0 z-0 overflow-hidden opacity-80">
      {doodles.map((d) => (
        <div
          key={d.id}
          className="animate-doodle-wiggle absolute pointer-events-auto"
          style={{ left: `${d.left}%`, top: `${d.top}%`, '--wiggle': `${d.rotate}deg` } as React.CSSProperties}
        >
          <Doodle size={d.size} color={d.color} delay={d.delay} duration={d.duration} onClick={() => {}} />
        </div>
      ))}
    </div>
  );
}

/** Public rooms that are waiting for players or already playing — one click to join. */
function OpenGames({
  rooms,
  busy,
  onJoin,
}: {
  rooms: GuestRoomInfo[] | null;
  busy: boolean;
  onJoin: (roomId: string) => void;
}) {
  return (
    <section className="mt-16" aria-labelledby="open-games">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="open-games" className="text-4xl">
          Open <span className="highlight">games</span>
        </h2>
        <p className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
          <span className="relative flex h-2.5 w-2.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-go opacity-60" />
            <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-go" />
          </span>
          {rooms === null ? 'Looking for games…' : `${rooms.length} public ${rooms.length === 1 ? 'game' : 'games'} · updates live`}
        </p>
      </div>

      {rooms !== null && rooms.length === 0 && (
        <div className="mt-5 rounded-2xl border-2 border-dashed border-ink/30 px-4 py-8 text-center">
          <p className="font-hand text-2xl">No public games right now.</p>
          <p className="mt-1 text-sm text-ink-soft">Hit Play to start one — others will see it here.</p>
        </div>
      )}

      {rooms !== null && rooms.length > 0 && (
        <ul className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {rooms.map((r) => {
            const playing = r.status === 'PLAYING';
            const shown = r.players.slice(0, 5);
            return (
              <li key={r.roomId} className="sheet-flat flex flex-col p-4">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="truncate text-2xl leading-tight" title={r.name}>
                    {r.name}
                  </h3>
                  {playing ? (
                    <span className="tag shrink-0 bg-marker-soft">
                      Round {Math.min(r.currentRound + 1, r.totalRounds)}/{r.totalRounds}
                    </span>
                  ) : (
                    <span className="tag shrink-0 border-go text-go">Waiting</span>
                  )}
                </div>

                <div className="mt-3 flex items-center">
                  {shown.map((p, i) => (
                    <span key={p.playerId} className="-ml-2 first:ml-0 rounded-full bg-white" style={{ zIndex: shown.length - i }} title={p.nickname}>
                      <Avatar avatar={p} size={32} />
                    </span>
                  ))}
                  {r.players.length > shown.length && (
                    <span className="-ml-2 flex h-8 w-8 items-center justify-center rounded-full border-2 border-ink bg-paper-deep text-xs font-bold">
                      +{r.players.length - shown.length}
                    </span>
                  )}
                </div>

                <p className="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-sm text-ink-soft">
                  <span className="inline-flex items-center gap-1">
                    <Users size={14} /> {r.playerCount}/{r.maxPlayers}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Globe size={14} /> {LANGUAGES.find((l) => l.code === r.language)?.label ?? 'English'}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Clock size={14} /> {r.drawingTimeSec}s
                  </span>
                </p>

                <button className="btn-secondary mt-4 w-full !text-lg" disabled={busy} onClick={() => onJoin(r.roomId)}>
                  {playing ? 'Jump in' : 'Join'}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

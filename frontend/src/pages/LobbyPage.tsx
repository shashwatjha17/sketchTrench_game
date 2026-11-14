import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import * as React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { guestApi, pingBackend } from '../api';
import { ApiError } from '../api';
import { createGuestSession, resumeSession } from '../guest';
import type { GuestSession } from '../guest';
import Avatar from '../components/Avatar';
import Doodle from '../components/Doodle';
import Canvas from '../components/Canvas';

const WORDMARK = 'SketchTrench';
const WORDMARK_COLORS = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6'];

const COLORS = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#f4a261', '#6d5dfc'];
const EXPRESSIONS = ['happy', 'sad', 'angry', 'surprised', 'wink'];
const WIGS = ['none', 'curly', 'bob', 'spiky'];
const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'de', label: 'Deutsch' },
  { code: 'fr', label: 'Français' },
  { code: 'es', label: 'Español' },
];

const DEFAULT_WORDS = ['mountain', 'cactus', 'umbrella', 'rocket'];

export default function LobbyPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const inviteCode = params.get('join')?.trim() ?? '';
  const [session, setSession] = useState<GuestSession | null>(null);
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState({
    avatarColor: '#6d5dfc',
    avatarExpression: 'happy',
    avatarSunglasses: false,
    avatarWig: 'none',
  });
  const [language, setLanguage] = useState('en');
  const [roomCode, setRoomCode] = useState(inviteCode);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  // 'checking' → first ping in flight; 'waking' → slow (free-tier server is cold-starting)
  const [server, setServer] = useState<'checking' | 'waking' | 'ready' | 'down'>('checking');

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
        customWordList: DEFAULT_WORDS,
      });
      setSession(s);
      navigate(`/rooms/${room.roomId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create a room');
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
        .filter((r) => !r.isPrivate && r.status === 'WAITING' && r.playerCount < r.maxPlayers)
        .sort((a, b) => b.playerCount - a.playerCount);
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

  const joinByCode = async () => {
    const code = roomCode.trim();
    if (!code) return;
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

  return (
    <div className="mx-auto w-full max-w-6xl space-y-12 px-4 py-10 sm:px-8">
      {/* HERO */}
      <section className="relative overflow-hidden rounded-[2rem] border-2 border-amber-200 bg-white px-3 py-12 text-center sm:px-6 sm:py-16 shadow-lg shadow-amber-100">
        <DoodleScatter count={12} minSize={20} maxSize={100} />
        <div className="relative z-10 mx-auto max-w-2xl rounded-3xl bg-white/85 px-2 py-4 sm:px-4 backdrop-blur-[2px]">
          <h1 className="text-4xl font-extrabold tracking-tight min-[400px]:text-5xl sm:text-6xl">
            {WORDMARK.split('').map((c, i) => (
              <span key={i} style={{ color: WORDMARK_COLORS[i % WORDMARK_COLORS.length] }}>
                {c}
              </span>
            ))}
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-lg text-slate-500">
            The free drawing &amp; guessing game. Sketch it, spell it, score it.
          </p>
        </div>
      </section>

      {/* JOIN CARD */}
      <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
        <form onSubmit={onPlay} className="space-y-5 rounded-[2rem] border-2 border-amber-100 bg-white p-6 shadow-lg shadow-amber-100 lg:sticky lg:top-6">
          <div className="flex items-center justify-center">
            <Avatar avatar={avatar} size={96} className="rounded-full ring-4 ring-[#6d5dfc]/20" />
          </div>

          {inviteCode && (
            <p className="rounded-xl bg-[#6d5dfc]/10 px-3 py-2 text-center text-sm font-semibold text-[#6d5dfc]">
              🎉 You're invited to room <span className="font-mono">{inviteCode}</span> — pick a nickname and hit Play!
            </p>
          )}
          {server === 'waking' && (
            <p role="status" className="flex items-center gap-2 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-800">
              <span className="h-3 w-3 shrink-0 animate-spin rounded-full border-2 border-amber-700 border-t-transparent" />
              Waking up the game server — the first visit can take up to a minute.
            </p>
          )}
          {server === 'down' && (
            <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-600">
              Can't reach the game server right now. Please try again in a minute.
            </p>
          )}
          {error && <p className="rounded bg-red-50 px-3 py-2 text-sm text-red-500">{error}</p>}

          <label className="block text-sm font-semibold text-slate-600">
            Nickname
            <input
              className="input mt-1 text-center"
              placeholder="Your nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
              minLength={1}
              maxLength={20}
            />
          </label>

          {/* avatar builder */}
          <div className="space-y-3">
            <p className="text-sm font-semibold text-slate-600">Your avatar</p>
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">Color</p>
              <div className="flex flex-wrap justify-center gap-2">
                {COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setAvatar((a) => ({ ...a, avatarColor: c }))}
                    className={`h-8 w-8 rounded-full border-2 transition ${avatar.avatarColor === c ? 'scale-110 border-slate-700' : 'border-slate-200'}`}
                    style={{ backgroundColor: c }}
                    aria-label={`color ${c}`}
                  />
                ))}
              </div>
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">Expression</p>
              <div className="flex flex-wrap justify-center gap-2">
                {EXPRESSIONS.map((e) => (
                  <button
                    key={e}
                    type="button"
                    onClick={() => setAvatar((a) => ({ ...a, avatarExpression: e }))}
                    className={`rounded-full px-3 py-1 text-sm capitalize transition ${avatar.avatarExpression === e ? 'bg-[#6d5dfc] text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                  >
                    {e}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-slate-400">Wig</p>
              <div className="flex flex-wrap justify-center gap-2">
                {WIGS.map((w) => (
                  <button
                    key={w}
                    type="button"
                    onClick={() => setAvatar((a) => ({ ...a, avatarWig: w }))}
                    className={`rounded-full px-3 py-1 text-sm capitalize transition ${avatar.avatarWig === w ? 'bg-[#6d5dfc] text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                  >
                    {w}
                  </button>
                ))}
              </div>
            </div>
            <label className="flex items-center justify-center gap-2 text-sm font-medium text-slate-500">
              <input
                type="checkbox"
                checked={avatar.avatarSunglasses}
                onChange={(e) => setAvatar((a) => ({ ...a, avatarSunglasses: e.target.checked }))}
              />
              Sunglasses
            </label>
          </div>

          <label className="block text-sm font-semibold text-slate-600">
            Language
            <select
              className="input mt-1"
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

          <div className="space-y-2 pt-1">
            <button
              type="submit"
              disabled={busy || !nickname.trim()}
              className="animate-play-pulse w-full rounded-full bg-green-500 px-4 py-3 text-xl font-bold text-white shadow-lg shadow-green-200 transition hover:scale-[1.02] hover:bg-green-400 disabled:opacity-50"
            >
              {busy ? (server === 'ready' ? 'Joining…' : 'Waking server…') : inviteCode ? '▶ Join room' : '▶ Play'}
            </button>
            <button
              type="button"
              disabled={busy || !nickname.trim()}
              onClick={() => enterRoom(true)}
              className="btn-ghost w-full !py-3"
            >
              Create Private Room
            </button>
          </div>

          <div className="flex gap-2 border-t border-slate-100 pt-4">
            <input
              className="input !py-2 text-center font-mono"
              placeholder="Room code"
              aria-label="Room code"
              value={roomCode}
              onChange={(e) => setRoomCode(e.target.value)}
              maxLength={8}
            />
            <button
              type="button"
              disabled={busy || !roomCode.trim()}
              onClick={joinByCode}
              className="btn whitespace-nowrap !px-4 !py-2"
            >
              Join
            </button>
          </div>
          <p className="mt-4 text-center text-sm text-slate-400">
            No account needed — you'll stay in this browser until you leave.
          </p>
        </form>

      <div className="space-y-3">
        <p className="text-center font-semibold text-slate-500">
          ✏️ Sketch while you wait — have fun with the board!
        </p>
        <Canvas height={440} />
        <p className="text-center text-xs text-slate-400">
          Just for you — your doodles are not broadcast to anyone.
        </p>
      </div>
    </section>

      {/* HOW TO PLAY */}
      <section className="rounded-[2rem] border-2 border-amber-100 bg-white p-6 shadow-lg shadow-amber-100 sm:p-8">
        <h2 className="text-center text-2xl font-extrabold text-slate-700">How to play</h2>
        <ol className="mt-6 grid gap-4 sm:grid-cols-3">
          {[
            ['✏️', 'Draw', 'Each round one player picks a word and sketches it on the chalkboard — no letters allowed!'],
            ['💬', 'Guess', 'Everyone else types guesses in the chat. The faster you get it, the more points you earn.'],
            ['🏆', 'Win', 'The drawer scores for every correct guess. Most points after all rounds wins.'],
          ].map(([icon, title, text]) => (
            <li key={title} className="rounded-2xl bg-amber-50/60 p-4 text-center">
              <div className="text-3xl" aria-hidden>{icon}</div>
              <h3 className="mt-2 font-bold text-slate-700">{title}</h3>
              <p className="mt-1 text-sm text-slate-500">{text}</p>
            </li>
          ))}
        </ol>
        <p className="mt-5 text-center text-sm text-slate-400">
          Playing with friends? Create a private room and share the invite link.
        </p>
      </section>

      <footer className="pb-4 text-center text-sm text-slate-400">
        Built by Shashwat Jha ·{' '}
        <a
          className="font-semibold text-[#6d5dfc] hover:underline"
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
        left = rnd() * 95;
        top = rnd() * 90;
      } while (left > 12 && left < 80 && top > 8 && top < 75);
      return { left, top };
    };
    return Array.from({ length: count }, (_, i) => ({
      id: i,
      size: minSize + rnd() * (maxSize - minSize),
      ...edgePos(),
      rotate: (rnd() - 0.5) * 28,
      delay: rnd() * 2,
      duration: 1.2 + rnd() * 2,
      color: COLORS[Math.floor(rnd() * COLORS.length)],
    }));
  }, [count, minSize, maxSize]);

  return (
    <div aria-hidden className="absolute inset-0 z-0 overflow-hidden">
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

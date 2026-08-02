import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { stomp } from '../ws';
import { guestApi } from '../api';
import { loadSession, connectWS } from '../guest';
import Canvas from '../components/Canvas';
import Avatar from '../components/Avatar';
import type {
  Avatar as AvatarData,
  CorrectGuess,
  GameEnded,
  GuestGameState,
  RoundEnded,
  RoundStarted,
  SecretWord,
  Stroke,
  TimerUpdate,
  TypingIndicator,
  WordOptions,
} from '../types';

type Phase = 'starting' | 'choosing' | 'drawing' | 'reveal' | 'ended';

interface PlayerInfo {
  username: string;
  avatar: Partial<AvatarData>;
}

interface GameState {
  phase: Phase;
  roundNumber: number;
  drawerId: string | null;
  drawerName: string;
  drawingTimeSec: number;
  remaining: number;
  secretWord: string | null;
  wordOptions: WordOptions | null;
  revealedWord: string | null;
  scores: Record<string, number>;
  winner: { id: string | null; name: string | null } | null;
  lastGuess: CorrectGuess | null;
}

const initial: GameState = {
  phase: 'starting',
  roundNumber: 0,
  drawerId: null,
  drawerName: '',
  drawingTimeSec: 0,
  remaining: 0,
  secretWord: null,
  wordOptions: null,
  revealedWord: null,
  scores: {},
  winner: null,
  lastGuess: null,
};

export default function GamePage() {
  const { id } = useParams();
  const roomId = id!;
  const navigate = useNavigate();
  const [session] = useState(() => loadSession());
  const [state, setState] = useState<GameState>(initial);
  const [messages, setMessages] = useState<{ username: string; text: string }[]>([]);
  const [typing, setTyping] = useState<{ userId: string; username: string } | null>(null);
  const [guess, setGuess] = useState('');
  const [players, setPlayers] = useState<Record<string, PlayerInfo>>({});
  const chatEndRef = useRef<HTMLDivElement>(null);
  const [celebration, setCelebration] = useState<CorrectGuess | null>(null);
  const celebTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const phaseAdvanced = useRef(false);
  const [replay, setReplay] = useState<{ v: number; strokes: Stroke[] }>({ v: 0, strokes: [] });

  useEffect(() => {
    if (!session) navigate('/', { replace: true });
    else connectWS();
  }, [session, navigate]);

  // pull player names + avatars from the room (game broadcasts only carry ids)
  useEffect(() => {
    guestApi
      .getRoom(roomId)
      .then((room) => {
        const map: Record<string, PlayerInfo> = {};
        for (const p of room.players) {
          map[p.playerId] = { username: p.nickname, avatar: p };
        }
        setPlayers(map);
      })
      .catch(() => {});
  }, [roomId]);

  useEffect(() => {
    if (!session) return;
    const t = setTimeout(async () => {
      if (phaseAdvanced.current) return;
      try {
        const st = await guestApi.state(session.playerId, roomId);
        // fresh join / mid-game refresh: pull the current state instead of waiting for
        // broadcasts we may have missed during navigation
        if (st.active && !phaseAdvanced.current) applyGameState(st);
      } catch {
        /* no live game */
      }
    }, 800);
    return () => clearTimeout(t);
  }, [session, roomId]);

  const applyGameState = useCallback(
    (st: GuestGameState) => {
      setState((s) => ({
        ...s,
        phase: st.active ? 'drawing' : 'starting',
        roundNumber: st.roundNumber,
        drawerId: st.drawerId,
        drawerName: st.drawerName,
        drawingTimeSec: st.drawingTimeSec,
        remaining: st.remainingSeconds,
        scores: st.scores,
        secretWord: st.secretWord,
        revealedWord: null,
        lastGuess: null,
      }));
      setReplay({ v: Date.now(), strokes: st.strokes });
    },
    [],
  );

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const amDrawer = state.drawerId !== null && state.drawerId === session?.playerId;

  useEffect(() => {
    const unsubs = [
      stomp.subscribe(`/topic/game/${roomId}`, (payload) => onGameMessage(payload)),
      stomp.subscribe('/user/queue/word-options', (payload) =>
        setState((s) => ({ ...s, wordOptions: payload as WordOptions, phase: 'choosing' })),
      ),
      stomp.subscribe('/user/queue/word', (payload) =>
        setState((s) => ({ ...s, secretWord: (payload as SecretWord).word })),
      ),
    ];
    return () => unsubs.forEach((u) => u());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  const onGameMessage = useCallback((payload: unknown) => {
    const msg = payload as Record<string, unknown>;
    const advance = () => {
      phaseAdvanced.current = true;
    };
    if (msg.roundNumber !== undefined && msg.drawerId !== undefined) {
      if ((msg as Record<string, unknown>).optionCount !== undefined) {
        advance();
        setState((s) => ({ ...s, roundNumber: msg.roundNumber as number, phase: 'starting' }));
      } else {
        const rs = msg as unknown as RoundStarted;
        advance();
        setState((s) => ({
          ...s,
          phase: 'drawing',
          roundNumber: rs.roundNumber,
          drawerId: rs.drawerId,
          drawerName: rs.drawerName,
          drawingTimeSec: rs.drawingTimeSec,
          remaining: rs.drawingTimeSec,
          revealedWord: null,
          lastGuess: null,
        }));
      }
    } else if (msg.remainingSeconds !== undefined) {
      setState((s) => ({ ...s, remaining: (msg as unknown as TimerUpdate).remainingSeconds }));
    } else if (msg.userId !== undefined && msg.points !== undefined && msg.remainingGuessers !== undefined) {
      const cg = msg as unknown as CorrectGuess;
      setState((s) => ({
        ...s,
        lastGuess: cg,
        scores: { ...s.scores, [cg.userId]: (s.scores[cg.userId] ?? 0) + cg.points },
      }));
      setCelebration(cg);
      if (celebTimer.current) clearTimeout(celebTimer.current);
      celebTimer.current = setTimeout(() => setCelebration(null), 2600);
    } else if (msg.word !== undefined && msg.drawerBonus !== undefined) {
      const re = msg as unknown as RoundEnded;
      setState((s) => ({ ...s, phase: 'reveal', revealedWord: re.word, scores: re.scores }));
    } else if (msg.winnerId !== undefined) {
      const ge = msg as unknown as GameEnded;
      setState((s) => ({ ...s, phase: 'ended', winner: { id: ge.winnerId, name: ge.winnerName }, scores: ge.scores }));
    } else if (msg.username !== undefined && msg.text !== undefined) {
      const cm = msg as unknown as { username: string; text: string };
      setMessages((m) => [...m.slice(-199), { username: cm.username, text: cm.text }]);
    } else if (msg.userId !== undefined && msg.username !== undefined) {
      const t = msg as unknown as TypingIndicator;
      setTyping(t);
      setTimeout(() => setTyping((cur) => (cur?.userId === t.userId ? null : cur)), 2000);
    }
  }, []);

  const sendMessage = (e: FormEvent) => {
    e.preventDefault();
    const text = guess.trim();
    if (!text) return;
    stomp.publish(`/app/guest/chat/${roomId}`, { words: [text] });
    setGuess('');
  };

  const pickWord = (wordId: string) => {
    stomp.publish(`/app/guest/game/${roomId}/word-select`, { wordId });
    setState((s) => ({ ...s, wordOptions: null, phase: 'drawing' }));
  };

  const leave = async () => {
    if (session) {
      await guestApi.leaveRoom(session.playerId, roomId).catch(() => {});
    }
    navigate('/');
  };

  const roundLabel = state.roundNumber === 0 ? 'Getting ready…' : `Round ${state.roundNumber}`;

  return (
    <div className="mx-auto w-full max-w-6xl space-y-4 px-4 py-6">
      {/* top bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border-2 border-amber-100 bg-white px-4 py-3 shadow-sm shadow-amber-50">
        <div>
          <p className="font-bold text-slate-700">{roundLabel}</p>
          <p className="text-sm text-slate-400">
            {state.phase === 'ended'
              ? 'Game over'
              : amDrawer
                ? 'You are drawing!'
                : state.drawerName
                  ? `${state.drawerName} is drawing`
                  : 'Waiting for the round…'}
          </p>
        </div>
        <AlarmClock remaining={state.remaining} total={state.drawingTimeSec} active={state.phase === 'drawing'} />
        <button className="btn-ghost !py-1 text-sm" onClick={leave}>
          Leave
        </button>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
        {/* canvas + secret word */}
        <div>
          {amDrawer && state.secretWord && state.phase === 'drawing' && (
            <div className="mb-2 rounded-full border-2 border-[#6d5dfc] bg-white px-4 py-2 text-center text-lg font-bold text-[#6d5dfc]">
              Your word: {state.secretWord}
            </div>
          )}
          <Canvas key={`${state.roundNumber}-${replay.v}`} roomId={roomId} readOnly={!amDrawer} initialStrokes={replay.strokes} />
          {state.phase === 'reveal' && state.revealedWord && (
            <div className="mt-3 rounded-2xl border-2 border-green-200 bg-green-50 px-4 py-3 text-center text-slate-700">
              The word was <span className="font-bold text-green-600">{state.revealedWord}</span>
            </div>
          )}
        </div>

        {/* side panel */}
        <div className="space-y-4">
          <Scoreboard scores={state.scores} drawerId={state.drawerId} players={players} currentPlayerId={session?.playerId} />

          <div className="rounded-2xl border-2 border-amber-100 bg-white p-3 shadow-sm shadow-amber-50">
            <h3 className="mb-2 text-sm font-semibold text-slate-500">Chat</h3>
            <div className="h-40 space-y-1 overflow-y-auto text-sm">
              {messages.map((m, i) => (
                <p key={i}>
                  <span className="text-[#6d5dfc]">{m.username}:</span> <span>{m.text}</span>
                </p>
              ))}
              {typing && <p className="text-xs italic text-slate-400">{typing.username} is typing…</p>}
              <div ref={chatEndRef} />
            </div>
            <form onSubmit={sendMessage} className="mt-2 flex gap-2">
              <input
                className="input !py-1 text-sm"
                placeholder={!amDrawer && state.phase === 'drawing' ? 'Type your guess or say something…' : 'Say something…'}
                value={guess}
                onChange={(e) => {
                  setGuess(e.target.value);
                  stomp.publish(`/app/guest/typing/${roomId}`, {});
                }}
                maxLength={200}
              />
              <button className="btn !py-1 text-sm" type="submit" disabled={!guess.trim()}>
                Send
              </button>
            </form>
            {state.lastGuess && !amDrawer && state.phase === 'drawing' && (
              <p className="mt-1 text-sm text-green-500">
                You guessed it! +{state.lastGuess.points} points
              </p>
            )}
          </div>
        </div>
      </div>

      {/* word chooser modal */}
      {state.wordOptions && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/50">
          <div className="rounded-2xl border-2 border-amber-100 bg-white p-6 text-center shadow-xl">
            <h2 className="mb-4 text-lg font-bold text-slate-700">Pick your word</h2>
            <div className="flex flex-wrap justify-center gap-3">
              {state.wordOptions.options.map((o) => (
                <button key={o.id} className="btn !px-6 !py-3 text-base" onClick={() => pickWord(o.id)}>
                  {o.text}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* game over modal */}
      {state.phase === 'ended' && (
        <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/50">
          <div className="rounded-2xl border-2 border-amber-100 bg-white p-8 text-center shadow-xl">
            <h2 className="text-2xl font-bold text-slate-700">
              {state.winner?.name ? `${state.winner.name} wins!` : 'Game over'}
            </h2>
            <div className="my-4 space-y-1">
              {Object.entries(state.scores)
                .sort((a, b) => b[1] - a[1])
                .map(([pid, pts]) => (
                  <p key={pid} className="text-sm text-slate-600">
                    {players[pid]?.username ?? `Player ${pid}`}: {pts} pts
                  </p>
                ))}
            </div>
            <button className="btn" onClick={leave}>
              Back to lobby
            </button>
          </div>
        </div>
      )}

      {/* celebration when someone guesses the word */}
      {celebration && (
        <div className="pointer-events-none fixed inset-0 z-20 overflow-hidden">
          <Confetti />
          <div className="flex h-full items-center justify-center">
            <div className="celebrate-pop rounded-2xl border-2 border-amber-100 bg-white px-8 py-6 text-center shadow-xl">
              <div className="text-4xl">🎉</div>
              <div className="mt-2 text-2xl font-bold text-[#6d5dfc]">
                {celebration.username} guessed the word!
              </div>
              <div className="mt-1 text-lg text-slate-500">
                +{celebration.points} points
                {celebration.remainingGuessers > 0
                  ? ` · ${celebration.remainingGuessers} left to guess`
                  : ' · everyone got it!'}
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

/** Analog alarm clock showing the round countdown; rings in the final seconds. */
function AlarmClock({ remaining, total, active }: { remaining: number; total: number; active: boolean }) {
  const low = active && remaining <= 10;
  const pct = total > 0 ? Math.max(0, Math.min(1, remaining / total)) : 0;
  const angle = -135 + pct * 270;

  return (
    <div className={`relative h-16 w-16 ${low ? 'animate-alarm-ring' : ''}`}>
      <svg viewBox="0 0 64 64" className="h-full w-full">
        <circle cx="32" cy="34" r="24" fill={low ? '#fef2f2' : '#fff'} stroke={low ? '#ef4444' : '#cbd5e1'} strokeWidth="3" />
        {Array.from({ length: 12 }, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          const r1 = 22;
          const r2 = i % 3 === 0 ? 18 : 19.5;
          return (
            <line
              key={i}
              x1={32 + Math.cos(a) * r1}
              y1={34 + Math.sin(a) * r1}
              x2={32 + Math.cos(a) * r2}
              y2={34 + Math.sin(a) * r2}
              stroke={low ? '#ef4444' : '#94a3b8'}
              strokeWidth={i % 3 === 0 ? 2 : 1.2}
            />
          );
        })}
        <g transform={`rotate(${angle} 32 34)`}>
          <line x1="32" y1="34" x2="32" y2="16" stroke={low ? '#ef4444' : '#6d5dfc'} strokeWidth="3" strokeLinecap="round" />
        </g>
        <circle cx="32" cy="34" r="2.5" fill={low ? '#ef4444' : '#6d5dfc'} />
        <circle cx="16" cy="12" r="4.5" fill={low ? '#ef4444' : '#cbd5e1'} />
        <circle cx="48" cy="12" r="4.5" fill={low ? '#ef4444' : '#cbd5e1'} />
        <rect x="26" y="6" width="12" height="6" rx="3" fill={low ? '#ef4444' : '#cbd5e1'} />
      </svg>
      <div
        className={`absolute inset-0 flex items-center justify-center pt-3 text-base font-bold ${
          low ? 'text-red-500' : 'text-slate-700'
        }`}
      >
        {active ? remaining : '—'}
      </div>
    </div>
  );
}

const CONFETTI_EMOJI = ['🎉', '🎊', '✨', '⭐', '🎨', '🖌️'];

function Confetti() {
  const pieces = useMemo(
    () =>
      Array.from({ length: 28 }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delay: Math.random() * 0.8,
        duration: 1.8 + Math.random() * 1.4,
        size: 16 + Math.random() * 18,
        emoji: CONFETTI_EMOJI[i % CONFETTI_EMOJI.length],
      })),
    [],
  );
  return (
    <div className="pointer-events-none absolute inset-0">
      {pieces.map((p) => (
        <span
          key={p.id}
          className="confetti-piece"
          style={{
            left: `${p.left}%`,
            fontSize: `${p.size}px`,
            animationDuration: `${p.duration}s`,
            animationDelay: `${p.delay}s`,
          }}
        >
          {p.emoji}
        </span>
      ))}
    </div>
  );
}

function Scoreboard({
  scores,
  drawerId,
  players,
  currentPlayerId,
}: {
  scores: Record<string, number>;
  drawerId: string | null;
  players: Record<string, PlayerInfo>;
  currentPlayerId: string | null | undefined;
}) {
  return (
    <div className="rounded-2xl border-2 border-amber-100 bg-white p-3 shadow-sm shadow-amber-50">
      <h3 className="mb-2 text-sm font-semibold text-slate-500">Players</h3>
      <ul className="space-y-1.5 text-sm">
        {Object.entries(scores)
          .sort((a, b) => b[1] - a[1])
          .map(([pid, pts]) => {
            const p = players[pid];
            return (
              <li key={pid} className="flex items-center justify-between">
                <span className="flex items-center gap-2">
                  <Avatar avatar={p?.avatar ?? {}} size={28} />
                  <span className="font-semibold text-slate-700">
                    {p?.username ?? `Player ${pid.slice(0, 6)}`}
                    {pid === drawerId && <span className="ml-1 text-xs">🖌</span>}
                    {pid === currentPlayerId && <span className="ml-1 text-xs text-[#6d5dfc]">(you)</span>}
                  </span>
                </span>
                <span className="font-semibold text-slate-500">{pts}</span>
              </li>
            );
          })}
        {Object.keys(scores).length === 0 && <li className="text-slate-400">No scores yet</li>}
      </ul>
    </div>
  );
}

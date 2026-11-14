import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Bot, Check, Flame, LogOut, Pencil, Send, Trophy, UserX, Volume2, VolumeX } from 'lucide-react';
import { stomp } from '../ws';
import { guestApi } from '../api';
import { loadSession, connectWS } from '../guest';
import Canvas from '../components/Canvas';
import Avatar from '../components/Avatar';
import { sounds, useMuted } from '../sounds';
import type {
  Avatar as AvatarData,
  BoardEvent,
  CloseGuess,
  CorrectGuess,
  GuestRoomInfo,
  GameEnded,
  GuestGameState,
  RoundEnded,
  RoundStarted,
  RoundStarting,
  SecretWord,
  SystemMessage,
  TimerUpdate,
  TypingIndicator,
  WordOptions,
} from '../types';

type Phase = 'starting' | 'choosing' | 'drawing' | 'reveal' | 'ended';

interface PlayerInfo {
  username: string;
  avatar: Partial<AvatarData>;
  isBot: boolean;
}

/** chat = normal message · system = server notice · close = your near-miss (only you see it) · correct = someone got it */
interface ChatLine {
  username: string;
  text: string;
  kind: 'chat' | 'system' | 'close' | 'correct';
}

interface GameState {
  phase: Phase;
  roundNumber: number;
  totalRounds: number;
  drawerId: string | null;
  drawerName: string;
  drawingTimeSec: number;
  remaining: number;
  secretWord: string | null;
  wordPattern: string | null;
  wordOptions: WordOptions | null;
  revealedWord: string | null;
  scores: Record<string, number>;
  /** players who guessed the word this round */
  guessed: string[];
  winner: { id: string | null; name: string | null } | null;
  /** points you scored this round by guessing (null = not guessed yet) */
  myGuessPoints: number | null;
}

const initial: GameState = {
  phase: 'starting',
  roundNumber: 0,
  totalRounds: 0,
  drawerId: null,
  drawerName: '',
  drawingTimeSec: 0,
  remaining: 0,
  secretWord: null,
  wordPattern: null,
  wordOptions: null,
  revealedWord: null,
  scores: {},
  guessed: [],
  winner: null,
  myGuessPoints: null,
};

export default function GamePage() {
  const { id } = useParams();
  const roomId = id!;
  const navigate = useNavigate();
  const [session] = useState(() => loadSession());
  const [state, setState] = useState<GameState>(initial);
  const [messages, setMessages] = useState<ChatLine[]>([]);
  const [muted, setMuted] = useMuted();
  const myId = session?.playerId;
  const [typing, setTyping] = useState<{ userId: string; username: string } | null>(null);
  const [guess, setGuess] = useState('');
  const [players, setPlayers] = useState<Record<string, PlayerInfo>>({});
  const chatBoxRef = useRef<HTMLDivElement>(null);
  const [celebration, setCelebration] = useState<CorrectGuess | null>(null);
  const celebTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const phaseAdvanced = useRef(false);
  const [replay, setReplay] = useState<{ v: number; strokes: BoardEvent[] }>({ v: 0, strokes: [] });
  const pushLine = useCallback((line: ChatLine) => setMessages((m) => [...m.slice(-199), line]), []);

  useEffect(() => {
    if (!session) navigate('/', { replace: true });
    else connectWS();
  }, [session, navigate]);

  // pull player names + avatars from the room (game broadcasts only carry ids), and keep
  // them current as people join, leave or get kicked
  const applyRoom = useCallback((room: GuestRoomInfo) => {
    const map: Record<string, PlayerInfo> = {};
    for (const p of room.players) {
      map[p.playerId] = { username: p.nickname, avatar: p, isBot: p.isBot };
    }
    setPlayers(map);
    setState((s) => ({
      ...s,
      totalRounds: room.totalRounds,
      // everyone in the room shows on the scoreboard, even before scoring; drop leavers
      scores: Object.fromEntries(room.players.map((p) => [p.playerId, s.scores[p.playerId] ?? p.score ?? 0])),
    }));
  }, []);

  useEffect(() => {
    guestApi.getRoom(roomId).then(applyRoom).catch(() => {});
    return stomp.subscribe(`/topic/room/${roomId}`, (payload) => applyRoom(payload as GuestRoomInfo));
  }, [roomId, applyRoom]);

  const applyGameState = useCallback((st: GuestGameState) => {
    setState((s) => ({
      ...s,
      phase: !st.active ? 'starting' : st.choosing ? 'choosing' : 'drawing',
      roundNumber: st.roundNumber,
      totalRounds: st.totalRounds || s.totalRounds,
      drawerId: st.drawerId,
      drawerName: st.drawerName,
      drawingTimeSec: st.drawingTimeSec,
      remaining: st.remainingSeconds,
      scores: st.scores,
      secretWord: st.secretWord,
      wordPattern: st.wordPattern,
      // the drawer may have missed the private word-options push while the page was loading
      wordOptions: st.wordOptions && st.wordOptions.length > 0 ? { roundNumber: st.roundNumber, options: st.wordOptions } : s.wordOptions,
      revealedWord: null,
    }));
    setReplay({ v: Date.now(), strokes: st.strokes });
  }, []);

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
    }, 400);
    return () => clearTimeout(t);
  }, [session, roomId, applyGameState]);

  // socket dropped and came back: resync the round instead of trusting stale local state
  useEffect(() => {
    if (!session) return;
    let skipNext = stomp.status !== 'connected';
    return stomp.onConnect(() => {
      if (skipNext) {
        skipNext = false;
        return;
      }
      guestApi
        .state(session.playerId, roomId)
        .then((st) => st.active && applyGameState(st))
        .catch(() => {});
    });
  }, [session, roomId, applyGameState]);

  useEffect(() => {
    const box = chatBoxRef.current;
    if (box) box.scrollTop = box.scrollHeight;
  }, [messages, typing]);

  const amDrawer = state.drawerId !== null && state.drawerId === session?.playerId;

  const onGameMessage = useCallback(
    (payload: unknown) => {
      const msg = payload as Record<string, unknown>;
      if (typeof msg.system === 'string') {
        pushLine({ username: '', text: msg.system, kind: 'system' });
      } else if (typeof msg.wordPattern === 'string' && msg.roundNumber === undefined) {
        // a letter was revealed
        sounds.hint();
        setState((s) => ({ ...s, wordPattern: msg.wordPattern as string }));
      } else if (msg.roundNumber !== undefined && msg.drawerId !== undefined && msg.word === undefined) {
        phaseAdvanced.current = true;
        if (msg.optionCount !== undefined) {
          const rs = msg as unknown as RoundStarting;
          setState((s) => ({
            ...s,
            phase: 'choosing',
            roundNumber: rs.roundNumber,
            drawerId: rs.drawerId,
            drawerName: rs.drawerName,
            secretWord: null,
            wordPattern: null,
            revealedWord: null,
            remaining: 0,
            guessed: [],
            myGuessPoints: null,
          }));
        } else {
          const rs = msg as unknown as RoundStarted;
          setState((s) => ({
            ...s,
            phase: 'drawing',
            roundNumber: rs.roundNumber,
            drawerId: rs.drawerId,
            drawerName: rs.drawerName,
            drawingTimeSec: rs.drawingTimeSec,
            remaining: rs.drawingTimeSec,
            wordPattern: rs.wordPattern ?? null,
            wordOptions: null,
            revealedWord: null,
            guessed: [],
            myGuessPoints: null,
          }));
        }
      } else if (msg.remainingSeconds !== undefined) {
        const left = (msg as unknown as TimerUpdate).remainingSeconds;
        if (left > 0 && left <= 10) sounds.tick();
        setState((s) => ({ ...s, remaining: left }));
      } else if (msg.userId !== undefined && msg.points !== undefined && msg.remainingGuessers !== undefined) {
        const cg = msg as unknown as CorrectGuess;
        const mine = cg.userId === myId;
        if (mine) sounds.youGuessed();
        else sounds.correct();
        pushLine({ username: cg.username, text: mine ? 'You guessed the word!' : `${cg.username} guessed the word!`, kind: 'correct' });
        setState((s) => ({
          ...s,
          guessed: [...s.guessed, cg.userId],
          myGuessPoints: mine ? cg.points : s.myGuessPoints,
          scores: { ...s.scores, [cg.userId]: (s.scores[cg.userId] ?? 0) + cg.points },
        }));
        setCelebration(cg);
        if (celebTimer.current) clearTimeout(celebTimer.current);
        celebTimer.current = setTimeout(() => setCelebration(null), 2200);
      } else if (msg.word !== undefined && msg.drawerBonus !== undefined) {
        const re = msg as unknown as RoundEnded;
        sounds.roundEnd();
        setState((s) => ({ ...s, phase: 'reveal', revealedWord: re.word, scores: re.scores, wordOptions: null }));
      } else if (msg.winnerId !== undefined) {
        const ge = msg as unknown as GameEnded;
        sounds.gameEnd();
        setState((s) => ({ ...s, phase: 'ended', winner: { id: ge.winnerId, name: ge.winnerName }, scores: ge.scores }));
      } else if (msg.username !== undefined && msg.text !== undefined) {
        const cm = msg as unknown as { username: string; text: string };
        pushLine({ username: cm.username, text: cm.text, kind: 'chat' });
      } else if (msg.userId !== undefined && msg.username !== undefined) {
        const t = msg as unknown as TypingIndicator;
        if (t.userId === myId) return;
        setTyping(t);
        setTimeout(() => setTyping((cur) => (cur?.userId === t.userId ? null : cur)), 2000);
      }
    },
    [myId, pushLine],
  );

  useEffect(() => {
    const unsubs = [
      stomp.subscribe(`/topic/game/${roomId}`, (payload) => onGameMessage(payload)),
      stomp.subscribe('/user/queue/word-options', (payload) => {
        sounds.yourTurn();
        setState((s) => ({ ...s, wordOptions: payload as WordOptions, phase: 'choosing' }));
      }),
      // private notices: "close!" hints and blocked messages
      stomp.subscribe('/user/queue/hint', (payload) => {
        const p = payload as Partial<CloseGuess & SystemMessage>;
        if (p.closeGuess) {
          sounds.close();
          pushLine({ username: '', text: `"${p.closeGuess}" is close!`, kind: 'close' });
        } else if (p.system) {
          pushLine({ username: '', text: p.system, kind: 'system' });
        }
      }),
      stomp.subscribe('/user/queue/kicked', () => navigate('/?kicked=1', { replace: true })),
      stomp.subscribe('/user/queue/word', (payload) =>
        setState((s) => ({ ...s, secretWord: (payload as SecretWord).word, wordOptions: null })),
      ),
    ];
    return () => unsubs.forEach((u) => u());
  }, [roomId, onGameMessage, pushLine, navigate]);

  const kick = async (pid: string) => {
    if (!session) return;
    const name = players[pid]?.username ?? 'this player';
    if (!window.confirm(`Vote to kick ${name}?`)) return;
    try {
      await guestApi.voteKick(session.playerId, roomId, pid);
    } catch (err) {
      pushLine({ username: '', text: err instanceof Error ? err.message : 'Could not kick', kind: 'system' });
    }
  };

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

  const iGuessed = state.myGuessPoints !== null;
  const chatPlaceholder =
    amDrawer && state.phase === 'drawing'
      ? "Chat (you can't say the word)"
      : !amDrawer && state.phase === 'drawing' && !iGuessed
        ? 'Type your guess…'
        : 'Say something…';

  return (
    <div className="mx-auto w-full max-w-[1400px] px-3 pb-6 sm:px-6">
      {/* top bar: round · word · timer */}
      <div className="sticky top-0 z-20 -mx-3 bg-paper/95 px-3 pb-3 pt-3 backdrop-blur sm:-mx-6 sm:px-6">
        <div className="sheet grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-2 px-3 py-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:px-4">
          <div className="min-w-0">
            <p className="font-hand text-2xl leading-none">
              {state.roundNumber === 0
                ? 'Getting ready…'
                : `Round ${state.roundNumber}${state.totalRounds ? ` of ${state.totalRounds}` : ''}`}
            </p>
            <p className="truncate text-sm text-ink-soft">
              {state.phase === 'ended'
                ? 'Game over'
                : amDrawer
                  ? 'You are drawing'
                  : state.drawerName
                    ? `${state.drawerName} is drawing`
                    : 'Waiting for the round…'}
            </p>
          </div>

          <div className="col-span-2 row-start-2 flex justify-center sm:col-span-1 sm:col-start-2 sm:row-start-1">
            <WordDisplay state={state} amDrawer={amDrawer} iGuessed={iGuessed} />
          </div>

          <div className="flex items-center justify-end gap-2">
            <Timer remaining={state.remaining} total={state.drawingTimeSec} active={state.phase === 'drawing'} />
            <button
              className="icon-btn"
              onClick={() => setMuted(!muted)}
              aria-label={muted ? 'Unmute sounds' : 'Mute sounds'}
              title={muted ? 'Unmute sounds' : 'Mute sounds'}
            >
              {muted ? <VolumeX size={18} /> : <Volume2 size={18} />}
            </button>
            <button className="icon-btn" onClick={leave} aria-label="Leave game" title="Leave game">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_300px] xl:grid-cols-[220px_minmax(0,1fr)_300px]">
        {/* canvas */}
        <div className="relative lg:order-1 xl:order-2">
          <Canvas key={`${state.roundNumber}-${replay.v}`} roomId={roomId} readOnly={!amDrawer} initialStrokes={replay.strokes} />

          {state.phase === 'choosing' && !state.wordOptions && (
            <BoardNote>
              <p className="font-hand text-3xl leading-none">{state.drawerName || 'The artist'} is picking a word…</p>
              <p className="mt-1 text-sm text-ink-soft">Get your guessing fingers ready.</p>
            </BoardNote>
          )}
          {state.phase === 'reveal' && state.revealedWord && (
            <BoardNote>
              <p className="text-sm font-semibold text-ink-soft">The word was</p>
              <p className="font-hand text-5xl leading-tight">
                <span className="highlight">{state.revealedWord}</span>
              </p>
            </BoardNote>
          )}

          {/* someone guessed it */}
          {celebration && (
            <div className="pointer-events-none absolute inset-x-0 top-6 z-10 flex justify-center px-4">
              <div className="celebrate-pop flex items-center gap-3 rounded-2xl border-2 border-ink bg-white px-5 py-3 shadow-sketch">
                <Check size={26} className="text-go" strokeWidth={3} />
                <div>
                  <p className="font-hand text-2xl leading-none">
                    {celebration.userId === myId ? 'You got it!' : `${celebration.username} got it!`}
                  </p>
                  <p className="text-sm text-ink-soft">
                    +{celebration.points} points ·{' '}
                    {celebration.remainingGuessers > 0 ? `${celebration.remainingGuessers} still guessing` : 'everyone got it'}
                  </p>
                </div>
              </div>
            </div>
          )}
          {celebration?.userId === myId && <Confetti />}
        </div>

        {/* chat */}
        <section className="sheet flex flex-col overflow-hidden lg:order-2 lg:h-0 lg:min-h-full xl:order-3">
          <h2 className="border-b-2 border-ink px-3 py-1.5 text-2xl leading-none">Chat</h2>
          <div ref={chatBoxRef} className="h-56 space-y-1 overflow-y-auto px-3 py-2 text-[15px] lg:h-auto lg:min-h-0 lg:flex-1">
            {messages.length === 0 && (
              <p className="pt-2 text-center text-sm text-ink-faint">
                {amDrawer ? 'Guesses will show up here.' : 'Type your guesses below.'}
              </p>
            )}
            {messages.map((m, i) => (
              <ChatRow key={i} line={m} />
            ))}
            {typing && <p className="text-xs italic text-ink-faint">{typing.username} is typing…</p>}
          </div>
          <form onSubmit={sendMessage} className="flex gap-2 border-t-2 border-ink bg-paper p-2">
            <input
              className="input !py-1.5"
              placeholder={chatPlaceholder}
              value={guess}
              onChange={(e) => {
                setGuess(e.target.value);
                stomp.publish(`/app/guest/typing/${roomId}`, {});
              }}
              maxLength={200}
              aria-label="Chat or guess"
            />
            <button className="icon-btn !h-auto shrink-0 !bg-marker" type="submit" disabled={!guess.trim()} aria-label="Send">
              <Send size={18} />
            </button>
          </form>
        </section>

        {/* players */}
        <section className="lg:order-3 xl:order-1">
          <Scoreboard
            scores={state.scores}
            drawerId={state.drawerId}
            guessed={state.guessed}
            players={players}
            currentPlayerId={session?.playerId}
            onKick={kick}
          />
        </section>
      </div>

      {/* word chooser */}
      {state.wordOptions && (
        <Modal>
          <Pencil size={28} className="mx-auto" />
          <h2 className="mt-2 text-4xl leading-none">Your turn to draw!</h2>
          <p className="mt-1 text-ink-soft">Pick a word:</p>
          <div className="mt-5 flex flex-wrap justify-center gap-3">
            {state.wordOptions.options.map((o) => (
              <button key={o.id} className="btn-secondary btn-lg" onClick={() => pickWord(o.id)}>
                {o.text}
              </button>
            ))}
          </div>
        </Modal>
      )}

      {/* game over */}
      {state.phase === 'ended' && (
        <Modal>
          <Trophy size={40} className="mx-auto text-marker-deep" fill="currentColor" />
          <h2 className="mt-2 text-5xl leading-none">
            {state.winner?.name ? (
              <>
                <span className="highlight">{state.winner.id === myId ? 'You' : state.winner.name}</span> win
                {state.winner.id === myId ? '!' : 's!'}
              </>
            ) : (
              'Game over'
            )}
          </h2>
          {state.revealedWord && (
            <p className="mt-2 text-sm text-ink-soft">
              The last word was <span className="font-bold text-ink">{state.revealedWord}</span>
            </p>
          )}
          <ol className="mx-auto mt-5 max-w-xs space-y-1.5 text-left">
            {Object.entries(state.scores)
              .sort((a, b) => b[1] - a[1])
              .map(([pid, pts], i) => (
                <li
                  key={pid}
                  className={`flex items-center gap-3 rounded-xl border-2 px-3 py-1.5 ${i === 0 ? 'border-ink bg-marker-soft' : 'border-transparent'}`}
                >
                  <span className="w-5 font-hand text-2xl leading-none">{i + 1}</span>
                  <Avatar avatar={players[pid]?.avatar ?? {}} size={30} />
                  <span className="flex-1 truncate font-semibold">{players[pid]?.username ?? 'Player'}</span>
                  <span className="font-hand text-xl">{pts}</span>
                </li>
              ))}
          </ol>
          <button className="btn btn-lg mt-6" onClick={leave}>
            Back to the lobby
          </button>
        </Modal>
      )}
    </div>
  );
}

/** The word line in the top bar: your word if drawing, blanks if guessing. */
function WordDisplay({ state, amDrawer, iGuessed }: { state: GameState; amDrawer: boolean; iGuessed: boolean }) {
  if (state.phase === 'drawing' && amDrawer && state.secretWord) {
    return (
      <p className="font-hand text-3xl leading-none">
        <span className="mr-2 text-lg text-ink-soft">draw:</span>
        <span className="highlight">{state.secretWord}</span>
      </p>
    );
  }
  if (state.phase === 'drawing' && state.wordPattern) {
    const parts = state.wordPattern.split(' ');
    return (
      <div className="flex items-end gap-3">
        <span className="flex flex-wrap items-end justify-center gap-x-4 gap-y-1 font-hand text-3xl leading-none" aria-label={`${state.wordPattern.replace(/[^_]/g, '').length} letters`}>
          {parts.map((part, i) => (
            <span key={i} className="flex gap-1">
              {part.split('').map((c, j) =>
                c === '_' ? <span key={j} className="blank">&nbsp;</span> : <span key={j}>{c}</span>,
              )}
            </span>
          ))}
        </span>
        <span className="pb-0.5 text-sm font-semibold text-ink-faint">{parts.map((p) => p.length).join(' ')}</span>
        {iGuessed && (
          <span className="tag border-go bg-go text-white">
            <Check size={12} strokeWidth={3} /> got it
          </span>
        )}
      </div>
    );
  }
  if (state.phase === 'reveal' && state.revealedWord) {
    return <p className="font-hand text-2xl leading-none text-ink-soft">round over</p>;
  }
  if (state.phase === 'choosing') {
    return <p className="font-hand text-2xl leading-none text-ink-soft">{amDrawer ? 'pick a word…' : 'choosing a word…'}</p>;
  }
  return null;
}

/** Countdown ring; turns red and shakes for the last ten seconds. */
function Timer({ remaining, total, active }: { remaining: number; total: number; active: boolean }) {
  const low = active && remaining <= 10;
  const pct = active && total > 0 ? Math.max(0, Math.min(1, remaining / total)) : 0;
  const r = 19;
  const c = 2 * Math.PI * r;
  return (
    <div className={`relative h-12 w-12 shrink-0 ${low ? 'animate-alarm-ring' : ''}`} aria-label={active ? `${remaining} seconds left` : 'timer'} role="timer">
      <svg viewBox="0 0 48 48" className="h-full w-full -rotate-90">
        <circle cx="24" cy="24" r={r} fill="#fff" stroke="#1f1d1a" strokeWidth="2" />
        <circle
          cx="24"
          cy="24"
          r={r - 4}
          fill="none"
          stroke={low ? '#e03131' : '#ffd23f'}
          strokeWidth="7"
          strokeDasharray={`${pct * 2 * Math.PI * (r - 4)} ${c}`}
          style={{ transition: 'stroke-dasharray 0.9s linear' }}
        />
      </svg>
      <span className={`absolute inset-0 flex items-center justify-center font-hand text-xl leading-none ${low ? 'text-stop' : ''}`}>
        {active ? remaining : '–'}
      </span>
    </div>
  );
}

function ChatRow({ line }: { line: ChatLine }) {
  if (line.kind === 'chat') {
    return (
      <p className="break-words">
        <span className="font-bold">{line.username}</span> <span className="text-ink-soft">{line.text}</span>
      </p>
    );
  }
  if (line.kind === 'correct') {
    return (
      <p className="flex items-center gap-1.5 rounded-lg bg-go/10 px-2 py-0.5 font-semibold text-go">
        <Check size={15} strokeWidth={3} /> {line.text}
      </p>
    );
  }
  if (line.kind === 'close') {
    return (
      <p className="flex items-center gap-1.5 rounded-lg bg-marker-soft px-2 py-0.5 font-semibold text-ink">
        <Flame size={15} className="text-[#f76707]" /> {line.text}
      </p>
    );
  }
  return <p className="text-sm italic text-ink-faint">{line.text}</p>;
}

function BoardNote({ children }: { children: React.ReactNode }) {
  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center p-6">
      <div className="sheet -rotate-1 px-6 py-4 text-center">{children}</div>
    </div>
  );
}

function Modal({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-ink/40 p-4">
      <div className="sheet celebrate-pop w-full max-w-md p-6 text-center shadow-sketch-lg sm:p-8">{children}</div>
    </div>
  );
}

const CONFETTI_COLORS = ['#ffd23f', '#ff6b6b', '#74c0fc', '#63e6be', '#b197fc', '#ffffff'];

function Confetti() {
  const pieces = useMemo(
    () =>
      Array.from({ length: 30 }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delay: Math.random() * 0.6,
        duration: 1.6 + Math.random() * 1.2,
        w: 6 + Math.random() * 8,
        h: 10 + Math.random() * 10,
        color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
      })),
    [],
  );
  return (
    <div className="pointer-events-none fixed inset-0 z-20 overflow-hidden">
      {pieces.map((p) => (
        <span
          key={p.id}
          className="confetti-piece"
          style={{
            left: `${p.left}%`,
            width: p.w,
            height: p.h,
            backgroundColor: p.color,
            animationDuration: `${p.duration}s`,
            animationDelay: `${p.delay}s`,
          }}
        />
      ))}
    </div>
  );
}

function Scoreboard({
  scores,
  drawerId,
  guessed,
  players,
  currentPlayerId,
  onKick,
}: {
  scores: Record<string, number>;
  drawerId: string | null;
  guessed: string[];
  players: Record<string, PlayerInfo>;
  currentPlayerId: string | null | undefined;
  onKick: (playerId: string) => void;
}) {
  const ranked = Object.entries(scores).sort((a, b) => b[1] - a[1]);
  return (
    <div className="sheet overflow-hidden">
      <h2 className="border-b-2 border-ink px-3 py-1.5 text-2xl leading-none">Players</h2>
      <ul className="divide-y-2 divide-dashed divide-paper-line">
        {ranked.map(([pid, pts], i) => {
          const p = players[pid];
          const isDrawer = pid === drawerId;
          const got = guessed.includes(pid);
          const me = pid === currentPlayerId;
          return (
            <li key={pid} className={`group flex items-center gap-2 px-3 py-2 ${got ? 'bg-go/10' : me ? 'bg-marker-soft/60' : ''}`}>
              <span className="w-4 font-hand text-lg leading-none text-ink-faint">{i + 1}</span>
              <Avatar avatar={p?.avatar ?? {}} size={32} />
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-1 truncate text-sm font-bold">
                  <span className="truncate">{p?.username ?? 'Player'}</span>
                  {me && <span className="font-normal text-ink-faint">(you)</span>}
                  {p?.isBot && <Bot size={13} className="shrink-0 text-ink-faint" aria-label="bot" />}
                </p>
                <p className="font-hand text-base leading-none text-ink-soft">{pts} pts</p>
              </div>
              {isDrawer && <Pencil size={16} className="shrink-0" aria-label="drawing" />}
              {got && <Check size={17} className="shrink-0 text-go" strokeWidth={3} aria-label="guessed" />}
              {!me && p && !p.isBot && (
                <button
                  className="shrink-0 text-ink-faint opacity-0 transition hover:text-stop focus:opacity-100 group-hover:opacity-100"
                  onClick={() => onKick(pid)}
                  aria-label={`Vote to kick ${p.username}`}
                  title="Vote to kick"
                >
                  <UserX size={16} />
                </button>
              )}
            </li>
          );
        })}
        {ranked.length === 0 && <li className="px-3 py-2 text-sm text-ink-faint">No players yet</li>}
      </ul>
    </div>
  );
}

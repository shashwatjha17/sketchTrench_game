import { useEffect, useState } from 'react';

/**
 * Tiny synthesized sound effects (Web Audio API) — no audio files to download.
 * Browsers only allow audio after a user gesture; every game page is reached via a click,
 * and we lazily resume the context on first use.
 */

const MUTE_KEY = 'st_muted';
let ctx: AudioContext | null = null;
let muted = readMuted();
const muteListeners = new Set<(m: boolean) => void>();

function readMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === '1';
  } catch {
    return false;
  }
}

function audio(): AudioContext | null {
  if (muted) return null;
  try {
    ctx ??= new AudioContext();
    if (ctx.state === 'suspended') void ctx.resume();
    return ctx;
  } catch {
    return null;
  }
}

/** One short note: frequency (Hz), start offset and length (s). */
function tone(freq: number, start: number, dur: number, type: OscillatorType = 'sine', gain = 0.15) {
  const a = audio();
  if (!a) return;
  const t0 = a.currentTime + start;
  const osc = a.createOscillator();
  const g = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(gain, t0 + 0.01);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g).connect(a.destination);
  osc.start(t0);
  osc.stop(t0 + dur + 0.05);
}

export const sounds = {
  /** a letter of the word was revealed */
  hint: () => tone(988, 0, 0.09, 'sine', 0.08),
  /** someone (else) guessed the word */
  correct: () => {
    tone(660, 0, 0.12, 'triangle');
    tone(880, 0.1, 0.18, 'triangle');
  },
  /** you guessed it */
  youGuessed: () => [523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.08, 0.2, 'triangle', 0.18)),
  /** it's your turn to draw */
  yourTurn: () => {
    tone(440, 0, 0.15, 'square', 0.08);
    tone(660, 0.15, 0.25, 'square', 0.08);
  },
  /** your guess was one letter off */
  close: () => tone(520, 0, 0.12, 'sine', 0.12),
  /** last-seconds clock tick */
  tick: () => tone(1200, 0, 0.04, 'square', 0.05),
  /** round over */
  roundEnd: () => {
    tone(392, 0, 0.18, 'triangle');
    tone(330, 0.16, 0.28, 'triangle');
  },
  /** game over fanfare */
  gameEnd: () => [523, 523, 523, 659, 784].forEach((f, i) => tone(f, i * 0.12, i === 4 ? 0.5 : 0.1, 'triangle', 0.18)),
};

export function setMuted(m: boolean) {
  muted = m;
  try {
    localStorage.setItem(MUTE_KEY, m ? '1' : '0');
  } catch {
    /* storage unavailable — keep in memory */
  }
  muteListeners.forEach((fn) => fn(m));
}

export function useMuted(): [boolean, (m: boolean) => void] {
  const [m, setM] = useState(muted);
  useEffect(() => {
    muteListeners.add(setM);
    return () => {
      muteListeners.delete(setM);
    };
  }, []);
  return [m, setMuted];
}

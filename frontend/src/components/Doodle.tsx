import { useMemo, useState } from 'react';
import * as React from 'react';

/**
 * Hand-drawn-looking doodle rendered as an SVG. Lines are jittered so they read as
 * "sketched by a person", not machine-perfect, and every stroke animates drawing
 * itself in on load (see `.doodle-line` in index.css). Type and pose are picked
 * randomly once per mount from a seed so the page never looks identical twice.
 */

const TYPES = ['star', 'heart', 'flower', 'house', 'cat', 'sun', 'moon', 'rainbow', 'spiral', 'ghost', 'rocket', 'butterfly'] as const;
type DoodleType = (typeof TYPES)[number];

const COLORS = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#f4a261'];

type Pt = [number, number];

const rng = (seed: number) => {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
};

/** n points around a shape with per-point radial wobble, returning a closed path. */
function blob(cx: number, cy: number, r: number, n: number, rnd: () => number, wob: number): string {
  const pts: Pt[] = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const rr = r * (1 + (rnd() - 0.5) * wob);
    pts.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr]);
  }
  return `M${pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' L')} Z`;
}

/** Freehand-ish line through points, overshooting slightly to feel penned. */
function scribble(pts: Pt[], wob: number, rnd: () => number): string {
  const jittered: Pt[] = pts.map(([x, y]) => [x + (rnd() - 0.5) * wob, y + (rnd() - 0.5) * wob]);
  let d = `M${jittered[0][0].toFixed(1)},${jittered[0][1].toFixed(1)}`;
  for (let i = 1; i < jittered.length; i++) {
    const [px, py] = jittered[i - 1];
    const [x, y] = jittered[i];
    const mx = (px + x) / 2;
    d += ` Q${px.toFixed(1)},${py.toFixed(1)} ${mx.toFixed(1)},${((py + y) / 2).toFixed(1)}`;
  }
  return d + ` L${jittered[jittered.length - 1][0].toFixed(1)},${jittered[jittered.length - 1][1].toFixed(1)}`;
}

function pathsFor(type: DoodleType, rnd: () => number): JSX.Element[] {
  switch (type) {
    case 'star': {
      const cx = 25, cy = 25, inner = 11, outer = 23;
      const pts: Pt[] = [];
      for (let i = 0; i < 10; i++) {
        const a = (i / 10) * Math.PI * 2 - Math.PI / 2;
        const r = i % 2 === 0 ? outer : inner;
        pts.push([cx + Math.cos(a) * r * (1 + (rnd() - 0.5) * 0.2), cy + Math.sin(a) * r * (1 + (rnd() - 0.5) * 0.2)]);
      }
      return [<path key="s" d={scribble(pts, 1.5, rnd)} />];
    }
    case 'heart':
      return [
        <path
          key="h"
          d={scribble(
            [
              [25, 42], [6, 22], [9, 7], [25, 13], [41, 7], [44, 22],
            ],
            2, rnd,
          )}
        />,
        <path key="h2" d={scribble([[25, 42], [25, 42]], 0, rnd)} opacity={0} />,
      ];
    case 'flower':
      return [
        <path key="c" d={blob(25, 25, 5, 8, rnd, 0.4)} />,
        ...Array.from({ length: 6 }, (_, i) => {
          const a = (i / 6) * Math.PI * 2;
          const x = 25 + Math.cos(a) * 13;
          const y = 25 + Math.sin(a) * 13;
          return <path key={`p${i}`} d={blob(x, y, 6, 10, rnd, 0.5)} />;
        }),
        <path key="st" d={scribble([[25, 30], [25, 46], [22, 48]], 0.8, rnd)} />,
      ];
    case 'house':
      return [
        <path key="h" d={scribble([[8, 38], [8, 16], [25, 4], [42, 16], [42, 38]], 1.4, rnd)} />,
        <path key="d" d={scribble([[19, 38], [19, 28], [31, 28], [31, 38]], 1.2, rnd)} />,
        <path key="w" d={scribble([[12, 22], [17, 18]], 0.7, rnd)} />,
      ];
    case 'cat':
      return [
        <path key="b" d={blob(25, 30, 14, 12, rnd, 0.35)} />,
        <path key="e1" d={blob(18, 26, 2.6, 8, rnd, 0.5)} />,
        <path key="e2" d={blob(32, 26, 2.6, 8, rnd, 0.5)} />,
        <path key="n" d={scribble([[25, 32], [25, 32]], 0, rnd)} opacity={0} />,
        <path key="w" d={scribble([[19, 38], [25, 41], [31, 38]], 0.8, rnd)} />,
        <path key="l" d={scribble([[15, 13], [13, 4], [19, 8]], 0.8, rnd)} />,
        <path key="r" d={scribble([[35, 13], [37, 4], [31, 8]], 0.8, rnd)} />,
        <path key="wh" d={scribble([[22, 36], [25, 34], [28, 36]], 0.5, rnd)} />,
      ];
    case 'sun':
      return [
        <path key="c" d={blob(25, 25, 9, 10, rnd, 0.3)} />,
        ...Array.from({ length: 8 }, (_, i) => {
          const a = (i / 8) * Math.PI * 2;
          return (
            <path
              key={`r${i}`}
              d={scribble(
                [
                  [25 + Math.cos(a) * 12, 25 + Math.sin(a) * 12],
                  [25 + Math.cos(a) * 18, 25 + Math.sin(a) * 18],
                ],
                0.9, rnd,
              )}
            />
          );
        }),
        <path key="m" d={scribble([[20, 26], [25, 30], [30, 26]], 0.5, rnd)} />,
      ];
    case 'moon':
      return [
        <path key="m" d={blob(25, 25, 15, 14, rnd, 0.3)} />,
        <path key="cut" d={blob(33, 19, 12, 12, rnd, 0.4)} />,
      ];
    case 'rainbow':
      return [0, 1, 2].map((i) => (
        <path
          key={`r${i}`}
          d={scribble(
            [[8, 40], [14, 18], [25, 10], [36, 18], [42, 40]],
            1.2 + i * 0.3, rnd,
          )}
          transform={`translate(0 ${i * 3.4})`}
        />
      ));
    case 'spiral':
      return [
        <path
          key="sp"
          d={(() => {
            const pts: Pt[] = [];
            for (let t = 0; t < 5.6; t += 0.28) {
              const rr = 2 + t * 3.6;
              pts.push([25 + Math.cos(t * 1.4) * rr, 25 + Math.sin(t * 1.4) * rr]);
            }
            return scribble(pts, 0.8, rnd);
          })()}
        />,
      ];
    case 'ghost':
      return [
        <path
          key="g"
          d={scribble(
            [
              [10, 42], [10, 24], [14, 10], [25, 6], [36, 10], [40, 24], [40, 42],
              [35, 37], [30, 42], [25, 37], [20, 42], [15, 37],
            ],
            1.6, rnd,
          )}
        />,
        <path key="e1" d={blob(18, 22, 2.6, 8, rnd, 0.5)} />,
        <path key="e2" d={blob(32, 22, 2.6, 8, rnd, 0.5)} />,
      ];
    case 'rocket':
      return [
        <path
          key="b"
          d={scribble(
            [
              [13, 40], [18, 22], [25, 6], [32, 22], [37, 40],
            ],
            1.5, rnd,
          )}
        />,
        <path key="w" d={blob(25, 16, 5, 10, rnd, 0.4)} />,
        <path key="f1" d={scribble([[13, 40], [10, 47], [18, 44]], 0.9, rnd)} />,
        <path key="f2" d={scribble([[37, 40], [40, 47], [32, 44]], 0.9, rnd)} />,
      ];
    case 'butterfly':
      return [
        <path key="l" d={blob(17, 28, 10, 10, rnd, 0.45)} />,
        <path key="r" d={blob(33, 28, 10, 10, rnd, 0.45)} />,
        <path key="b" d={blob(25, 24, 3.2, 8, rnd, 0.4)} />,
        <path key="a" d={scribble([[25, 6], [23, 14], [25, 18], [27, 14], [25, 6]], 0.7, rnd)} />,
      ];
  }
}

export default function Doodle({
  size = 48,
  color,
  type,
  className = '',
  delay = 0,
  duration = 1.6,
  style,
  onClick,
}: {
  size?: number;
  color?: string;
  type?: DoodleType;
  className?: string;
  delay?: number;
  duration?: number;
  style?: React.CSSProperties;
  onClick?: () => void;
}) {
  const [redraw, setRedraw] = useState(0);
  const { type: pick, stroke } = useMemo(() => {
    const seed = Math.floor(Math.random() * 0xffffffff) + redraw;
    const rnd = rng(seed);
    return {
      type: type ?? TYPES[Math.floor(rnd() * TYPES.length)],
      stroke: color ?? COLORS[Math.floor(rnd() * COLORS.length)],
    };
  }, [color, type, redraw]);

  const paths = useMemo(() => pathsFor(pick, () => Math.random()), [pick, redraw]);

  const handleClick = () => {
    setRedraw((r) => r + 1);
    onClick?.();
  };

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 50 50"
      fill="none"
      stroke={stroke}
      strokeWidth={2.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`doodle-line ${onClick ? 'doodle-interactive' : ''} ${className}`}
      onClick={onClick ? handleClick : undefined}
      style={{
        ...style,
        strokeDasharray: 1,
        animationDuration: `${duration}s`,
        animationDelay: `${delay}s`,
      }}
    >
      {paths.map((p, i) =>
        React.cloneElement(p, {
          key: i,
          pathLength: 1,
          style: { animationDelay: `${delay + i * (duration / Math.max(paths.length, 1) / 3)}s` },
        }),
      )}
    </svg>
  );
}

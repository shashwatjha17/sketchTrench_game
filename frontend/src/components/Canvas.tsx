import { useEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import { Eraser, PaintBucket, Pencil, Trash2, Undo2 } from 'lucide-react';
import { stomp } from '../ws';

interface Point {
  x: number;
  y: number;
}

export interface PathEvent {
  type: 'path';
  points: Point[];
  color: string;
  size: number;
}

export interface FillEvent {
  type: 'fill';
  color: string;
  x: number;
  y: number;
}

export interface ClearEvent {
  type: 'clear';
}

export interface UndoEvent {
  type: 'undo';
}

/** Board events that change pixels (kept in the history / replay log). */
export type BoardEvent = PathEvent | FillEvent | ClearEvent;
export type DrawEvent = BoardEvent | UndoEvent;

export interface Stroke {
  type: 'path';
  points: Point[];
  color: string;
  size: number;
}

const BACKGROUND = '#ffffff';
const PALETTE = [
  '#1f1d1a', '#8a847b', '#ffffff', '#e03131', '#f76707', '#ffd23f',
  '#2f9e44', '#1098ad', '#1c7ed6', '#7048e8', '#e64980', '#8d5524',
];
/** Logical drawing width. All coordinates (local + broadcast) live in this space, so every
 *  client agrees on positions regardless of its screen; the backing store is scaled up. */
const LOGICAL_WIDTH = 900;
const FILL_TOLERANCE = 48;
const MAX_HISTORY = 3000;
const BRUSH_SIZES = [3, 6, 12, 20];
const ERASER_SIZE = 24;

type Tool = 'brush' | 'eraser' | 'fill';

interface CanvasProps {
  /** Room to broadcast/receive strokes on. Omit for a purely local doodle pad. */
  roomId?: string;
  readOnly?: boolean;
  height?: number;
  /** Events to replay on mount (late join / refresh). Older servers sent paths only. */
  initialStrokes?: BoardEvent[];
}

/**
 * Paper drawing surface. With a roomId it's the shared board (strokes sent over
 * /topic/drawing/{id}, rendered on every client); without one it's a local doodle pad
 * for the landing/room pages. Tools: brush (sizes + full color spectrum), eraser,
 * paint-bucket fill, clear all. Eraser/fill/clear broadcast live but only paths are
 * persisted for replay.
 */
export default function Canvas({ roomId, readOnly = false, height = 560, initialStrokes }: CanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawingRef = useRef(false);
  const currentStroke = useRef<Point[]>([]);
  const lastFill = useRef<Point>({ x: 0, y: 0 });
  const [tool, setTool] = useState<Tool>('brush');
  const [color, setColor] = useState('#1f1d1a');
  const [size, setSize] = useState(6);
  // backing-store pixels per logical pixel (≥1, ≤3); set once on mount from DPR + display size
  const scaleRef = useRef(1);
  // everything drawn so far, in order — undo pops the last event and replays the rest
  const historyRef = useRef<BoardEvent[]>([]);
  const [canUndo, setCanUndo] = useState(false);
  // read inside the long-lived socket subscription, so it must not be a stale closure value
  const readOnlyRef = useRef(readOnly);
  readOnlyRef.current = readOnly;

  const publish = (payload: DrawEvent) => {
    if (roomId) stomp.publish(`/app/guest/drawing/${roomId}`, payload);
  };

  useEffect(() => {
    if (!roomId) return;
    const unsub = stomp.subscribe(`/topic/drawing/${roomId}`, (payload) => {
      // the server echoes the drawer's own events back; the drawer already applied them
      if (!readOnlyRef.current) return;
      const msg = payload as DrawEvent;
      if (msg.type === 'undo') {
        undoLocal();
      } else if (msg.type === 'clear' || msg.type === 'fill' || msg.type === 'path') {
        applyEvent(msg);
        record(msg);
      }
    });
    return unsub;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  const ctx = () => canvasRef.current?.getContext('2d');

  const applyEvent = (ev: BoardEvent) => {
    if (ev.type === 'clear') clearCanvas();
    else if (ev.type === 'fill') fillAt(ev.x, ev.y, ev.color);
    else if (ev.type === 'path') drawPath(ev);
  };

  const record = (ev: BoardEvent) => {
    const h = historyRef.current;
    h.push(ev);
    if (h.length > MAX_HISTORY) h.shift();
    setCanUndo(true);
  };

  /** Drop the last event and redraw everything else from scratch. */
  const undoLocal = () => {
    const h = historyRef.current;
    if (h.length === 0) return false;
    h.pop();
    clearCanvas();
    h.forEach(applyEvent);
    setCanUndo(h.length > 0);
    return true;
  };

  const undo = () => {
    if (readOnly) return;
    if (undoLocal()) publish({ type: 'undo' } satisfies UndoEvent);
  };

  const clearCanvas = () => {
    const c = canvasRef.current;
    const g = ctx();
    if (!c || !g) return;
    g.fillStyle = BACKGROUND;
    g.fillRect(0, 0, LOGICAL_WIDTH, height);
  };

  const drawPath = (stroke: Stroke) => {
    const g = ctx();
    if (!g || stroke.points.length < 2) return;
    g.strokeStyle = stroke.color;
    g.lineWidth = stroke.size;
    g.lineCap = 'round';
    g.lineJoin = 'round';
    g.beginPath();
    g.moveTo(stroke.points[0].x, stroke.points[0].y);
    for (let i = 1; i < stroke.points.length; i++) g.lineTo(stroke.points[i].x, stroke.points[i].y);
    g.stroke();
  };

  /** Bucket fill over the device-pixel buffer. (x, y) are logical coordinates.
   *  A generous tolerance swallows most anti-aliased stroke edges, and a 1px grow pass
   *  paints the remaining blended ring so no grey halo is left around lines. */
  const fillAt = (lx: number, ly: number, fillColor: string, tol = FILL_TOLERANCE) => {
    const c = canvasRef.current;
    const g = ctx();
    if (!c || !g) return;
    const w = c.width;
    const h = c.height;
    const x = Math.floor(lx * scaleRef.current);
    const y = Math.floor(ly * scaleRef.current);
    if (x < 0 || x >= w || y < 0 || y >= h) return;
    const img = g.getImageData(0, 0, w, h);
    const data = img.data;
    const start = (y * w + x) * 4;
    const [tr, tg, tb] = hexToRgb(fillColor);
    const sr = data[start];
    const sg = data[start + 1];
    const sb = data[start + 2];
    if (Math.abs(sr - tr) <= 2 && Math.abs(sg - tg) <= 2 && Math.abs(sb - tb) <= 2) return;
    const close = (i: number) =>
      Math.abs(data[i] - sr) <= tol && Math.abs(data[i + 1] - sg) <= tol && Math.abs(data[i + 2] - sb) <= tol;
    const filled = new Uint8Array(w * h);
    const stack: number[] = [y * w + x];
    while (stack.length > 0) {
      const p = stack.pop()!;
      if (filled[p]) continue;
      const i = p * 4;
      if (!close(i)) continue;
      filled[p] = 1;
      const px = p % w;
      if (px > 0) stack.push(p - 1);
      if (px < w - 1) stack.push(p + 1);
      if (p >= w) stack.push(p - w);
      if (p < w * (h - 1)) stack.push(p + w);
    }
    const paint = (p: number) => {
      const i = p * 4;
      data[i] = tr;
      data[i + 1] = tg;
      data[i + 2] = tb;
      data[i + 3] = 255;
    };
    // grow by one pixel into the anti-aliased border, then paint the region
    for (let p = 0; p < filled.length; p++) {
      if (filled[p] !== 1) continue;
      const px = p % w;
      const n = [px > 0 ? p - 1 : -1, px < w - 1 ? p + 1 : -1, p >= w ? p - w : -1, p < w * (h - 1) ? p + w : -1];
      for (const q of n) if (q >= 0 && filled[q] === 0) filled[q] = 2;
    }
    for (let p = 0; p < filled.length; p++) if (filled[p]) paint(p);
    g.putImageData(img, 0, 0);
  };

  const localPos = (e: ReactPointerEvent<HTMLCanvasElement>): Point => {
    const rect = canvasRef.current!.getBoundingClientRect();
    const scaleX = LOGICAL_WIDTH / rect.width;
    const scaleY = height / rect.height;
    return { x: (e.clientX - rect.left) * scaleX, y: (e.clientY - rect.top) * scaleY };
  };

  const onDown = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (readOnly) return;
    e.preventDefault();
    if (tool === 'fill') {
      const p = localPos(e);
      const strokeColor = color;
      const ev: FillEvent = { type: 'fill', x: Math.round(p.x), y: Math.round(p.y), color: strokeColor };
      fillAt(ev.x, ev.y, ev.color);
      record(ev);
      publish(ev);
      drawingRef.current = true;
      lastFill.current = p;
      return;
    }
    drawingRef.current = true;
    currentStroke.current = [localPos(e)];
  };

  const onMove = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current || readOnly) return;
    const p = localPos(e);
    if (tool === 'fill') {
      // paint-fill: keep flooding along the drag path (throttled by distance)
      const last = lastFill.current;
      if (Math.hypot(p.x - last.x, p.y - last.y) > 40) {
        const ev: FillEvent = { type: 'fill', x: Math.round(p.x), y: Math.round(p.y), color };
        fillAt(ev.x, ev.y, ev.color);
        record(ev);
        publish(ev);
        lastFill.current = p;
      }
      return;
    }
    const points = currentStroke.current;
    const g = ctx();
    if (g && points.length > 0) {
      const last = points[points.length - 1];
      g.strokeStyle = tool === 'eraser' ? BACKGROUND : color;
      g.lineWidth = tool === 'eraser' ? ERASER_SIZE : size;
      g.lineCap = 'round';
      g.beginPath();
      g.moveTo(last.x, last.y);
      g.lineTo(p.x, p.y);
      g.stroke();
    }
    points.push(p);
  };

  const onUp = () => {
    if (!drawingRef.current) return;
    drawingRef.current = false;
    if (currentStroke.current.length > 1) {
      const ev: PathEvent = {
        type: 'path',
        points: currentStroke.current,
        color: tool === 'eraser' ? BACKGROUND : color,
        size: tool === 'eraser' ? ERASER_SIZE : size,
      };
      record(ev);
      publish(ev);
    }
    currentStroke.current = [];
  };

  const onClear = () => {
    clearCanvas();
    const ev: ClearEvent = { type: 'clear' };
    record(ev);
    publish(ev);
  };

  // Ctrl/Cmd+Z undoes, unless you're typing in a text field (e.g. the chat)
  useEffect(() => {
    if (readOnly) return;
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.shiftKey || e.key.toLowerCase() !== 'z') return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
      e.preventDefault();
      undo();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly]);

  useEffect(() => {
    // size the backing store for the device's pixel density so lines stay crisp on
    // retina / HiDPI screens; drawing code keeps using logical coordinates via the transform
    const c = canvasRef.current;
    if (c) {
      const cssWidth = c.getBoundingClientRect().width || LOGICAL_WIDTH;
      const scale = Math.min(3, Math.max(1, (cssWidth * (window.devicePixelRatio || 1)) / LOGICAL_WIDTH));
      scaleRef.current = scale;
      c.width = Math.round(LOGICAL_WIDTH * scale);
      c.height = Math.round(height * scale);
      c.getContext('2d')?.setTransform(scale, 0, 0, scale, 0, 0);
    }
    clearCanvas();
    historyRef.current = [];
    initialStrokes?.forEach((ev) => {
      if (ev.type === 'path' && ev.points.length < 2) return;
      applyEvent(ev);
      record(ev);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="sheet overflow-hidden">
      <canvas
        ref={canvasRef}
        width={LOGICAL_WIDTH}
        height={height}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerLeave={onUp}
        className="block w-full touch-none"
        style={{ backgroundColor: BACKGROUND, cursor: readOnly ? 'default' : tool === 'fill' ? 'cell' : 'crosshair' }}
      />
      {!readOnly && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 border-t-2 border-ink bg-paper px-2 py-1.5 sm:gap-y-2 sm:px-2.5 sm:py-2">
          <div className="flex gap-1" role="group" aria-label="Tools">
            <ToolButton active={tool === 'brush'} onClick={() => setTool('brush')} label="Pencil">
              <Pencil size={18} />
            </ToolButton>
            <ToolButton active={tool === 'eraser'} onClick={() => setTool('eraser')} label="Eraser">
              <Eraser size={18} />
            </ToolButton>
            <ToolButton active={tool === 'fill'} onClick={() => setTool('fill')} label="Fill">
              <PaintBucket size={18} />
            </ToolButton>
          </div>

          <div className="order-last flex w-full flex-wrap items-center justify-between gap-[3px] sm:order-none sm:w-auto sm:justify-start sm:gap-1" role="group" aria-label="Colours">
            {PALETTE.map((c) => (
              <button
                key={c}
                onClick={() => {
                  setColor(c);
                  if (tool === 'eraser') setTool('brush');
                }}
                className={`h-5 w-5 rounded-full border-2 transition sm:h-6 sm:w-6 ${
                  color === c && tool !== 'eraser' ? 'scale-110 border-ink ring-2 ring-marker-deep ring-offset-1' : 'border-ink/60 hover:scale-110'
                }`}
                style={{ backgroundColor: c }}
                aria-label={`colour ${c}`}
                title={c}
              />
            ))}
            <label
              className="relative h-5 w-5 cursor-pointer overflow-hidden rounded-full border-2 border-ink/60 sm:h-6 sm:w-6"
              title="Custom colour"
              style={{ background: 'conic-gradient(#e03131, #ffd23f, #2f9e44, #1c7ed6, #7048e8, #e03131)' }}
            >
              <input
                type="color"
                value={color}
                onChange={(e) => {
                  setColor(e.target.value);
                  if (tool === 'eraser') setTool('brush');
                }}
                className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                aria-label="Custom colour"
              />
            </label>
          </div>

          {tool === 'brush' && (
            <div className="flex items-center gap-1" role="group" aria-label="Brush size">
              {BRUSH_SIZES.map((s) => (
                <button
                  key={s}
                  onClick={() => setSize(s)}
                  className={`flex h-8 w-8 items-center justify-center rounded-lg border-2 transition ${
                    size === s ? 'border-ink bg-marker' : 'border-transparent hover:bg-paper-deep'
                  }`}
                  aria-label={`${s}px brush`}
                  title={`${s}px`}
                >
                  <span className="rounded-full bg-ink" style={{ width: Math.min(s, 20), height: Math.min(s, 20) }} />
                </button>
              ))}
            </div>
          )}

          <div className="ml-auto flex gap-1">
            <button className="icon-btn" onClick={undo} disabled={!canUndo} title="Undo (Ctrl+Z)" aria-label="Undo">
              <Undo2 size={18} />
            </button>
            <button
              className="icon-btn hover:!bg-stop hover:text-white"
              onClick={onClear}
              title="Clear the board"
              aria-label="Clear the board"
            >
              <Trash2 size={18} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function ToolButton({
  active,
  onClick,
  label,
  children,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  children: ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      aria-pressed={active}
      title={label}
      className={`icon-btn ${active ? '!bg-marker shadow-sketch-sm' : ''}`}
    >
      {children}
    </button>
  );
}

function hexToRgb(hex: string): [number, number, number] {
  const m = hex.replace('#', '');
  const n = m.length === 3 ? m.split('').map((c) => c + c).join('') : m;
  const v = parseInt(n, 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

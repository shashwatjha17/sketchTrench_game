import { useEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
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

export type DrawEvent = PathEvent | FillEvent | ClearEvent;

export interface Stroke {
  type: 'path';
  points: Point[];
  color: string;
  size: number;
}

const BACKGROUND = '#1e3a34';
/** Logical drawing width. All coordinates (local + broadcast) live in this space, so every
 *  client agrees on positions regardless of its screen; the backing store is scaled up. */
const LOGICAL_WIDTH = 900;
const FILL_TOLERANCE = 48;
const BRUSH_SIZES = [3, 6, 12, 20];
const ERASER_SIZE = 24;

type Tool = 'brush' | 'eraser' | 'fill';

interface CanvasProps {
  /** Room to broadcast/receive strokes on. Omit for a purely local doodle pad. */
  roomId?: string;
  readOnly?: boolean;
  height?: number;
  initialStrokes?: Stroke[];
}

/**
 * Chalkboard drawing surface. With a roomId it's the shared board (strokes sent over
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
  const [color, setColor] = useState('#ffffff');
  const [size, setSize] = useState(6);
  // backing-store pixels per logical pixel (≥1, ≤3); set once on mount from DPR + display size
  const scaleRef = useRef(1);

  const publish = (payload: DrawEvent) => {
    if (roomId) stomp.publish(`/app/guest/drawing/${roomId}`, payload);
  };

  useEffect(() => {
    if (!roomId) return;
    const unsub = stomp.subscribe(`/topic/drawing/${roomId}`, (payload) => {
      const msg = payload as DrawEvent;
      if (msg.type === 'clear') {
        clearCanvas();
      } else if (msg.type === 'fill') {
        fillAt(msg.x, msg.y, msg.color);
      } else if (msg.type === 'path') {
        drawPath(msg);
      }
    });
    return unsub;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  const ctx = () => canvasRef.current?.getContext('2d');

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
      fillAt(p.x, p.y, strokeColor);
      publish({ type: 'fill', x: Math.round(p.x), y: Math.round(p.y), color: strokeColor } satisfies FillEvent);
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
        fillAt(p.x, p.y, color);
        publish({ type: 'fill', x: Math.round(p.x), y: Math.round(p.y), color } satisfies FillEvent);
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
      publish({
        type: 'path',
        points: currentStroke.current,
        color: tool === 'eraser' ? BACKGROUND : color,
        size: tool === 'eraser' ? ERASER_SIZE : size,
      } satisfies PathEvent);
    }
    currentStroke.current = [];
  };

  const onClear = () => {
    clearCanvas();
    publish({ type: 'clear' } satisfies ClearEvent);
  };

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
    initialStrokes?.forEach((s) => s.points.length > 1 && drawPath(s));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="rounded-2xl border-8 border-[#8b5a2b] bg-[#8b5a2b] p-1 shadow-xl shadow-amber-900/20">
      <div className="overflow-hidden rounded-md">
        <canvas
          ref={canvasRef}
          width={LOGICAL_WIDTH}
          height={height}
          onPointerDown={onDown}
          onPointerMove={onMove}
          onPointerUp={onUp}
          onPointerLeave={onUp}
          className="w-full touch-none"
          style={{ backgroundColor: BACKGROUND, cursor: tool === 'fill' ? 'pointer' : 'crosshair' }}
        />
      </div>
      {!readOnly && (
        <div className="mt-1 flex flex-wrap items-center gap-2 rounded-md bg-[#7a4d24] px-2 py-1.5">
          <ToolButton active={tool === 'brush'} onClick={() => setTool('brush')} label="✏️ Brush" />
          <ToolButton active={tool === 'eraser'} onClick={() => setTool('eraser')} label="🧽 Eraser" />
          <ToolButton active={tool === 'fill'} onClick={() => setTool('fill')} label="🪣 Fill" />
          <input
            type="color"
            value={color}
            onChange={(e) => {
              setColor(e.target.value);
              setTool('brush');
            }}
            className="h-7 w-9 cursor-pointer rounded border-0 bg-transparent p-0"
            aria-label="color spectrum"
            title="Color spectrum"
          />
          {tool === 'brush' && (
            <div className="flex items-center gap-1">
              {BRUSH_SIZES.map((s) => (
                <button
                  key={s}
                  onClick={() => setSize(s)}
                  className={`flex h-7 w-7 items-center justify-center rounded-full transition ${size === s ? 'bg-amber-200' : 'bg-[#6b421f] hover:bg-[#5d391a]'}`}
                  aria-label={`brush ${s}px`}
                  title={`${s}px brush`}
                >
                  <span className="rounded-full bg-white" style={{ width: s, height: s }} />
                </button>
              ))}
            </div>
          )}
          <button
            className="ml-auto rounded-full bg-red-600 px-3 py-1 text-xs font-bold text-white transition hover:bg-red-500"
            onClick={onClear}
          >
            Clear
          </button>
        </div>
      )}
    </div>
  );
}

function ToolButton({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-3 py-1 text-xs font-bold transition ${active ? 'bg-amber-200 text-slate-800' : 'bg-[#6b421f] text-amber-50 hover:bg-[#5d391a]'}`}
    >
      {label}
    </button>
  );
}

function hexToRgb(hex: string): [number, number, number] {
  const m = hex.replace('#', '');
  const n = m.length === 3 ? m.split('').map((c) => c + c).join('') : m;
  const v = parseInt(n, 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

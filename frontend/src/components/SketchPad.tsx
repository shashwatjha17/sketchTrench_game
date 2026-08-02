import { useEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';

interface Point {
  x: number;
  y: number;
}

const BACKGROUND = '#1e3a34';
const BRUSH_SIZES = [3, 6, 12, 20];

type Tool = 'brush' | 'eraser';

/**
 * Personal doodle pad for the landing / room pages — purely local, nothing is sent
 * over the network. Blackboard look with brush sizes, eraser, color spectrum and a
 * Clear button. Drawings are lost on refresh (kept out of scope; it's just a sketch).
 */
export default function SketchPad({ height = 240 }: { height?: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawingRef = useRef(false);
  const currentStroke = useRef<Point[]>([]);
  const [tool, setTool] = useState<Tool>('brush');
  const [color, setColor] = useState('#ffffff');
  const [size, setSize] = useState(6);

  const ctx = () => canvasRef.current?.getContext('2d');

  const clearCanvas = () => {
    const c = canvasRef.current;
    const g = ctx();
    if (!c || !g) return;
    g.fillStyle = BACKGROUND;
    g.fillRect(0, 0, c.width, c.height);
  };

  const drawLocal = (points: Point[], c: string, s: number) => {
    const g = ctx();
    if (!g || points.length < 2) return;
    g.strokeStyle = c;
    g.lineWidth = s;
    g.lineCap = 'round';
    g.lineJoin = 'round';
    g.beginPath();
    g.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) g.lineTo(points[i].x, points[i].y);
    g.stroke();
  };

  const localPos = (e: ReactPointerEvent<HTMLCanvasElement>): Point => {
    const rect = canvasRef.current!.getBoundingClientRect();
    const scaleX = canvasRef.current!.width / rect.width;
    const scaleY = canvasRef.current!.height / rect.height;
    return { x: (e.clientX - rect.left) * scaleX, y: (e.clientY - rect.top) * scaleY };
  };

  const onDown = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    drawingRef.current = true;
    currentStroke.current = [localPos(e)];
  };

  const onMove = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current) return;
    const p = localPos(e);
    const points = currentStroke.current;
    const g = ctx();
    if (g && points.length > 0) {
      const last = points[points.length - 1];
      g.strokeStyle = tool === 'eraser' ? BACKGROUND : color;
      g.lineWidth = tool === 'eraser' ? 24 : size;
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
      drawLocal(currentStroke.current, tool === 'eraser' ? BACKGROUND : color, tool === 'eraser' ? 24 : size);
    }
    currentStroke.current = [];
  };

  useEffect(() => {
    clearCanvas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="rounded-2xl border-8 border-[#8b5a2b] bg-[#8b5a2b] p-1 shadow-xl shadow-amber-900/20">
      <div className="overflow-hidden rounded-md">
        <canvas
          ref={canvasRef}
          width={900}
          height={height}
          onPointerDown={onDown}
          onPointerMove={onMove}
          onPointerUp={onUp}
          onPointerLeave={onUp}
          className="w-full touch-none"
          style={{ backgroundColor: BACKGROUND, cursor: 'crosshair' }}
        />
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-2 rounded-md bg-[#7a4d24] px-2 py-1.5">
        <button
          onClick={() => setTool('brush')}
          className={`rounded-full px-3 py-1 text-xs font-bold transition ${tool === 'brush' ? 'bg-amber-200 text-slate-800' : 'bg-[#6b421f] text-amber-50 hover:bg-[#5d391a]'}`}
        >
          ✏️ Brush
        </button>
        <button
          onClick={() => setTool('eraser')}
          className={`rounded-full px-3 py-1 text-xs font-bold transition ${tool === 'eraser' ? 'bg-amber-200 text-slate-800' : 'bg-[#6b421f] text-amber-50 hover:bg-[#5d391a]'}`}
        >
          🧽 Eraser
        </button>
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
          onClick={clearCanvas}
        >
          Clear
        </button>
      </div>
    </div>
  );
}

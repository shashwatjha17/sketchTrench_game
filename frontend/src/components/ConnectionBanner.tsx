import { WifiOff } from 'lucide-react';
import { useConnectionStatus } from '../ws';

/** Sticky banner shown while the shared socket is (re)connecting after it had connected once. */
export default function ConnectionBanner() {
  const status = useConnectionStatus();
  if (status !== 'reconnecting') return null;
  return (
    <div
      role="status"
      className="sticky top-0 z-50 flex items-center justify-center gap-2 border-b-2 border-ink bg-marker px-4 py-2 font-hand text-lg text-ink"
    >
      <WifiOff size={18} className="animate-pulse" />
      Connection lost — reconnecting…
    </div>
  );
}

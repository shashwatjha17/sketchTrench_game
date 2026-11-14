import { useConnectionStatus } from '../ws';

/** Sticky banner shown while the shared socket is (re)connecting after it had connected once. */
export default function ConnectionBanner() {
  const status = useConnectionStatus();
  if (status !== 'reconnecting') return null;
  return (
    <div
      role="status"
      className="sticky top-0 z-50 flex items-center justify-center gap-2 bg-amber-100 px-4 py-2 text-sm font-semibold text-amber-800 shadow"
    >
      <span className="h-3 w-3 animate-spin rounded-full border-2 border-amber-700 border-t-transparent" />
      Connection lost — reconnecting…
    </div>
  );
}

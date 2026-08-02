import type { Avatar } from '../types';

/**
 * Renders a customizable cartoon avatar: base color, facial expression, optional
 * sunglasses and wig. Pure SVG so it scales cleanly anywhere (scoreboard, game page,
 * profile). Unknown expression/wig values degrade to the default instead of crashing.
 */
export default function Avatar({
  avatar,
  size = 40,
  className = '',
}: {
  avatar: Partial<Avatar>;
  size?: number;
  className?: string;
}) {
  const color = avatar.avatarColor ?? '#6d5dfc';
  const expression = avatar.avatarExpression ?? 'happy';
  const sunglasses = avatar.avatarSunglasses ?? false;
  const wig = avatar.avatarWig ?? 'none';

  const eyes = expression === 'angry' ? (
    <>
      <path d="M17 20 L12 17" stroke="#333" strokeWidth="2.4" strokeLinecap="round" />
      <path d="M33 20 L38 17" stroke="#333" strokeWidth="2.4" strokeLinecap="round" />
      <circle cx="15.5" cy="21" r="2" fill="#333" />
      <circle cx="34.5" cy="21" r="2" fill="#333" />
    </>
  ) : expression === 'surprised' ? (
    <>
      <circle cx="17" cy="20" r="3.4" fill="#333" />
      <circle cx="33" cy="20" r="3.4" fill="#333" />
    </>
  ) : expression === 'sad' ? (
    <>
      <path d="M14 21 q3 -3 6 0" stroke="#333" strokeWidth="2.4" strokeLinecap="round" fill="none" />
      <path d="M30 21 q3 -3 6 0" stroke="#333" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    </>
  ) : expression === 'wink' ? (
    <>
      <circle cx="17" cy="20" r="2.6" fill="#333" />
      <path d="M30 20 q3 -3 6 0" stroke="#333" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    </>
  ) : (
    // happy (default): closed-up happy eyes
    <>
      <path d="M14 20 q3 -3 6 0" stroke="#333" strokeWidth="2.4" strokeLinecap="round" fill="none" />
      <path d="M30 20 q3 -3 6 0" stroke="#333" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    </>
  );

  const mouth =
    expression === 'surprised' ? (
      <ellipse cx="25" cy="31" rx="3" ry="3.6" fill="#7c2d12" />
    ) : expression === 'angry' ? (
      <path d="M21 33 h8" stroke="#7c2d12" strokeWidth="2.4" strokeLinecap="round" />
    ) : expression === 'sad' ? (
      <path d="M21 33 q4 -4 8 0" stroke="#7c2d12" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    ) : (
      <path d="M21 31 q4 4 8 0" stroke="#7c2d12" strokeWidth="2.4" strokeLinecap="round" fill="none" />
    );

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 50 50"
      className={className}
      role="img"
      aria-label="avatar"
    >
      {/* face */}
      <circle cx="25" cy="26" r="19" fill={color} stroke="#00000018" strokeWidth="1" />

      {/* wig fringe on top of the head */}
      {wig === 'curly' && (
        <>
          <circle cx="14" cy="13" r="4" fill="#4a3520" />
          <circle cx="20" cy="10" r="4.2" fill="#4a3520" />
          <circle cx="26" cy="9" r="4.2" fill="#4a3520" />
          <circle cx="32" cy="10" r="4.2" fill="#4a3520" />
          <circle cx="37" cy="13" r="4" fill="#4a3520" />
        </>
      )}
      {wig === 'bob' && (
        <>
          <path d="M10 20 Q12 8 25 8 Q38 8 40 20 L38 26 Q25 22 12 26 Z" fill="#4a3520" />
          <path d="M12 24 L9 32 Q12 34 14 30 Z" fill="#4a3520" />
          <path d="M38 24 L41 32 Q38 34 36 30 Z" fill="#4a3520" />
        </>
      )}
      {wig === 'spiky' && (
        <>
          <path d="M11 16 L13 8 L17 14 Z" fill="#4a3520" />
          <path d="M17 13 L19 5 L23 11 Z" fill="#4a3520" />
          <path d="M24 10 L26 4 L29 10 Z" fill="#4a3520" />
          <path d="M31 11 L34 5 L36 13 Z" fill="#4a3520" />
          <path d="M37 15 L40 9 L40 17 Z" fill="#4a3520" />
        </>
      )}

      {eyes}

      {/* sunglasses over the eyes */}
      {sunglasses && (
        <>
          <rect x="10" y="16" width="15" height="10" rx="3.5" fill="#1f2937" />
          <rect x="25" y="16" width="15" height="10" rx="3.5" fill="#1f2937" />
          <path d="M25 20 h5" stroke="#1f2937" strokeWidth="1.6" />
          <path d="M10 20 L7 18" stroke="#1f2937" strokeWidth="2" strokeLinecap="round" />
          <path d="M40 20 L43 18" stroke="#1f2937" strokeWidth="2" strokeLinecap="round" />
        </>
      )}

      {mouth}
    </svg>
  );
}

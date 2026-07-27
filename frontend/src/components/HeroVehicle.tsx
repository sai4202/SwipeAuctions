/**
 * Hand-drawn, animated car silhouette used to frame the homepage hero — deliberately not a stock
 * photo (see the CSS-only-backdrop rationale on `.hero` in index.css): a single inline SVG scales
 * to any size with no network request, and its brand-gradient fill/animated wheels/speed-lines read
 * as "premium vehicle auction" without competing with the headline the way a real photo did.
 */
export default function HeroVehicle({ side }: { side: 'left' | 'right' }) {
  // Both cars face inward toward the headline — the right-side one mirrors the same artwork
  // rather than needing a second hand-drawn (and separately animated) path.
  return (
    <div className={`hero-car hero-car-${side}${side === 'right' ? ' hero-car-flip' : ''}`} aria-hidden="true">
      <svg viewBox="0 0 240 110" className="hero-car-svg" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="heroCarBody" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--red)" />
            <stop offset="100%" stopColor="var(--red-2)" />
          </linearGradient>
        </defs>
        <g className="hero-car-speedlines">
          <line x1="-24" y1="38" x2="8" y2="38" />
          <line x1="-34" y1="52" x2="6" y2="52" />
          <line x1="-18" y1="66" x2="10" y2="66" />
        </g>
        <ellipse className="hero-car-shadow" cx="120" cy="98" rx="92" ry="9" />
        <path
          className="hero-car-body"
          fill="url(#heroCarBody)"
          d="M15,78 C15,64 24,58 34,57 L52,57 C58,40 76,24 100,22 L150,22 C168,22 180,32 188,48 L204,55 C216,58 225,66 225,78 L225,82 C225,86 222,90 217,90 L208,90 C208,76 197,66 184,66 C171,66 160,76 160,90 L80,90 C80,76 69,66 56,66 C43,66 32,76 32,90 L23,90 C18,90 15,86 15,82 Z"
        />
        <path className="hero-car-glass" d="M60,55 C66,40 80,28 100,26 L145,26 C158,26 168,32 176,44 L176,55 Z" />
        <g className="hero-car-wheel">
          <circle cx="56" cy="90" r="20" className="wheel-tire" />
          <circle cx="56" cy="90" r="9" className="wheel-hub" />
          <line x1="56" y1="90" x2="56" y2="71" className="wheel-spoke" />
          <line x1="56" y1="90" x2="72" y2="90" className="wheel-spoke" />
          <line x1="56" y1="90" x2="56" y2="109" className="wheel-spoke" />
          <line x1="56" y1="90" x2="40" y2="90" className="wheel-spoke" />
        </g>
        <g className="hero-car-wheel">
          <circle cx="184" cy="90" r="20" className="wheel-tire" />
          <circle cx="184" cy="90" r="9" className="wheel-hub" />
          <line x1="184" y1="90" x2="184" y2="71" className="wheel-spoke" />
          <line x1="184" y1="90" x2="200" y2="90" className="wheel-spoke" />
          <line x1="184" y1="90" x2="184" y2="109" className="wheel-spoke" />
          <line x1="184" y1="90" x2="168" y2="90" className="wheel-spoke" />
        </g>
      </svg>
    </div>
  )
}

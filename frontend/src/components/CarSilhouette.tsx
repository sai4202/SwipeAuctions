/**
 * Minimal single-tone car silhouette (fill="currentColor") — deliberately plainer than
 * HeroVehicle's illustrated car, so it can be reused as a small "traffic" motif or a huge, faint
 * drifting ghost shape elsewhere on the homepage without looking like the same asset repeated.
 */
export default function CarSilhouette({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 100 40" className={className} xmlns="http://www.w3.org/2000/svg" fill="currentColor">
      <path d="M6,30 C6,24 10,21 15,21 L22,21 C25,13 33,7 44,7 L64,7 C72,7 78,11 82,18 L90,21 C95,22 98,25 98,29 L98,31 C98,33 96,35 94,35 L90,35 C90,29 85,25 79,25 C73,25 68,29 68,35 L34,35 C34,29 29,25 23,25 C17,25 12,29 12,35 L9,35 C7,35 6,33 6,31 Z" />
      <circle cx="23" cy="35" r="6" fillOpacity=".55" />
      <circle cx="79" cy="35" r="6" fillOpacity=".55" />
    </svg>
  )
}

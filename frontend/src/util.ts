/** Milliseconds until an ISO (local, no-zone) datetime string. */
export function msUntil(iso: string): number {
  return new Date(iso).getTime() - Date.now()
}

/** Human countdown like "2d 03:14:05" or "00:42" from a millisecond delta. */
export function formatCountdown(ms: number): string {
  if (ms <= 0) return 'ended'
  const s = Math.floor(ms / 1000)
  const days = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  if (days > 0) return `${days}d ${pad(h)}:${pad(m)}:${pad(sec)}`
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(sec)}`
  return `${pad(m)}:${pad(sec)}`
}

export function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return '₹' + Number(n).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

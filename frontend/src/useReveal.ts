import { useEffect } from 'react'

/** Fades/slides in any `[data-reveal]` element the first time it scrolls into view. Re-runs the
 * scan whenever `deps` change, since category/event tiles render asynchronously after their fetch
 * resolves and would otherwise never get observed. */
export function useReveal(deps: unknown[] = []) {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    const els = document.querySelectorAll<HTMLElement>('[data-reveal]:not(.in-view)')
    if (els.length === 0) return
    if (!('IntersectionObserver' in window)) {
      els.forEach((el) => el.classList.add('in-view'))
      return
    }
    const io = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view')
          io.unobserve(entry.target)
        }
      })
    }, { threshold: 0.15, rootMargin: '0px 0px -8% 0px' })
    els.forEach((el) => io.observe(el))
    return () => io.disconnect()
  }, deps)
}

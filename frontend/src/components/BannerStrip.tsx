import { useEffect, useState } from 'react'
import { getBanners, type PublicBanner } from '../api'

/** Public promotional banner strip — admin-managed via /admin/banners, active-only, shown to every
 *  visitor (signed in or not) same as the homepage's featured-events teaser. Renders nothing if no
 *  banners are configured, so it never leaves an empty placeholder on a fresh install. */
export default function BannerStrip() {
  const [banners, setBanners] = useState<PublicBanner[]>([])

  useEffect(() => { getBanners().then(setBanners).catch(() => {}) }, [])

  if (banners.length === 0) return null

  return (
    <div style={{ display: 'flex', gap: 12, overflowX: 'auto', padding: '4px 0 12px' }}>
      {banners.map((b) => {
        const img = <img src={b.imageUrl} alt={b.title ?? 'Promotion'} style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
        const card = (
          <div style={{ position: 'relative', minWidth: 280, height: 120, borderRadius: 12, overflow: 'hidden', flexShrink: 0 }}>
            {img}
            {b.title && (
              <div style={{
                position: 'absolute', inset: 'auto 0 0 0', padding: '8px 12px',
                background: 'linear-gradient(0deg, rgba(0,0,0,.65), transparent)', color: '#fff', fontSize: 13, fontWeight: 700,
              }}>
                {b.title}
              </div>
            )}
          </div>
        )
        return b.linkUrl ? <a key={b.id} href={b.linkUrl} style={{ display: 'contents' }}>{card}</a> : <div key={b.id}>{card}</div>
      })}
    </div>
  )
}

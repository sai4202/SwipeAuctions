/**
 * Categories that use the seller-created "auction events" browsing flow (list events, enter one to
 * see its items) instead of jumping straight to a flat auction grid. All share the same UI (the
 * EventsBrowse component, mounted inside BrowsePage at /auctions?category=<slug>) — only this
 * metadata (label/icon/pill text) differs between them.
 */
export interface EventCategoryMeta {
  slug: string
  label: string
  icon: string
  /** Short text for the category pill bar (e.g. CarTrade shows "Banks / OEM" for bank vehicles). */
  pillLabel: string
}

export const EVENT_CATEGORIES: Record<string, EventCategoryMeta> = {
  'bank-vehicles': { slug: 'bank-vehicles', label: 'Bank Vehicles', icon: '🏛️', pillLabel: 'Banks / OEM' },
  insurance: { slug: 'insurance', label: 'Insurance', icon: '🛡️', pillLabel: 'Insurance' },
  premium: { slug: 'premium', label: 'Premium', icon: '⭐', pillLabel: 'Premium' },
  auto: { slug: 'auto', label: 'Auto', icon: '🚙', pillLabel: 'Auto' },
}

/** Slugs of every category that uses the events-first browsing flow. */
export const EVENT_CATEGORY_SLUGS: string[] = Object.keys(EVENT_CATEGORIES)

export function eventCategoryMeta(slug: string | undefined): EventCategoryMeta {
  return (slug && EVENT_CATEGORIES[slug]) || { slug: slug ?? '', label: 'Auction Events', icon: '📦', pillLabel: 'Events' }
}

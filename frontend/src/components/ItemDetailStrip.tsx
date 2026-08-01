import { DETAIL_FIELD_BY_KEY, REQUIRED_FOR_USED_VEHICLES } from '../detailFields'

const MAX_ITEMS = 4
const MAX_VALUE_LENGTH = 40
// Vehicle Type is already shown as its own badge elsewhere; the two free-text remarks fields and
// payment terms tend to run long paragraphs — none of those belong in a compact one-line strip.
const EXCLUDED_KEYS = new Set(['Vehicle Type', 'additionalRemarks', 'listingRemarks', 'paymentTerms'])

const MANDATORY_LABELS: Record<string, string> = {
  yardName: 'Yard Name', yardLocation: 'Yard Location', registrationNumber: 'Reg No', chassisNo: 'Chassis No',
}

function labelFor(key: string): string {
  return MANDATORY_LABELS[key] ?? DETAIL_FIELD_BY_KEY[key]?.label ?? key
}

/**
 * A quick "at a glance" summary of an item's most useful attributes, shown directly in list-view
 * rows (AuctionListTable, EventsBrowse's per-event table) instead of only on the detail page's
 * tabs — matching how the reference site always surfaces key specs right on the row instead of
 * making a buyer click into every item to see them. Not vehicle-specific: a listing's `attributes`
 * bag holds whatever's relevant to its own category (Reg No/Chassis No/Yard for a used vehicle,
 * RAM/Storage/Screen size for electronics, ...) via the generic per-category attribute system, so
 * this shows the first few non-blank entries generically rather than a fixed vehicle-only key list
 * — the 4 used-vehicle fields (see detailFields.ts's REQUIRED_FOR_USED_VEHICLES) are prioritized
 * first since they're the most consistently meaningful across the vehicle categories. Renders
 * nothing if the item has no attributes at all (e.g. a bare "sealed, no extra details" listing).
 */
export default function ItemDetailStrip({ attributes }: { attributes: Record<string, string> }) {
  const mandatory = REQUIRED_FOR_USED_VEHICLES
    .map((key) => ({ key, value: attributes[key]?.trim() }))
    .filter((e): e is { key: string; value: string } => Boolean(e.value))

  const rest = Object.entries(attributes)
    .filter(([key, value]) =>
      value?.trim() && !REQUIRED_FOR_USED_VEHICLES.includes(key) && !EXCLUDED_KEYS.has(key) && value.trim().length <= MAX_VALUE_LENGTH,
    )
    .map(([key, value]) => ({ key, value: value.trim() }))

  const items = [...mandatory, ...rest].slice(0, MAX_ITEMS)
  if (items.length === 0) return null

  return (
    <div className="vehicle-detail-strip">
      <div className="vehicle-detail-strip-row">
        {items.map((it) => (
          <span key={it.key}><span className="muted">{labelFor(it.key)}:</span> <b>{it.value}</b></span>
        ))}
      </div>
    </div>
  )
}

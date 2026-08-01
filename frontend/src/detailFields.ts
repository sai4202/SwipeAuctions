/**
 * Single source of truth for the structured "item details" fields (Yard Name, Registration
 * Number, Chassis No, Repo Date, ...) shown in the tabbed detail section on the auction detail
 * page, and collected via matching form fields in the admin "Add Stock" flow. Both consume this
 * same list so the two can never drift out of sync — a field added here shows up as both an admin
 * input and a detail-page card with zero other changes.
 *
 * Stored as plain `ListingAttribute` key/value rows (the existing generic per-listing attribute
 * bag) — `key` here is exactly the `ListingAttribute.key` / `Auction.attributes` map key.
 */

export type DetailTab = 'General Details' | 'Registration' | 'Insurance' | 'Other Details' | 'Remarks'

export type DetailFieldType = 'text' | 'yesno' | 'date' | 'number' | 'textarea'

export interface DetailFieldDef {
  key: string
  label: string
  icon: string
  type: DetailFieldType
  tab: DetailTab
}

export const DETAIL_TABS: DetailTab[] = ['General Details', 'Registration', 'Insurance', 'Other Details', 'Remarks']

/** Fallback tab for attribute keys that don't match anything below (e.g. older seeded items'
 *  Fuel/KM driven/Year attributes) — keeps them visible instead of silently dropping them. */
export const FALLBACK_TAB: DetailTab = 'General Details'
export const FALLBACK_ICON = 'ℹ'

export const DETAIL_FIELDS: DetailFieldDef[] = [
  // ---- General Details ----
  { key: 'powerSteering', label: 'Power Steering', icon: '🛞', type: 'yesno', tab: 'General Details' },
  { key: 'yardName', label: 'Yard Name', icon: '📍', type: 'text', tab: 'General Details' },
  { key: 'yardLocation', label: 'Yard Location', icon: '📍', type: 'text', tab: 'General Details' },
  { key: 'paymentTerms', label: 'Payment Terms', icon: '💳', type: 'text', tab: 'General Details' },
  { key: 'rcBookAvailable', label: 'RC Book Available', icon: '📘', type: 'yesno', tab: 'General Details' },
  { key: 'sellerReference', label: 'Seller Reference', icon: '👤', type: 'text', tab: 'General Details' },
  { key: 'sunRoof', label: 'Sun Roof', icon: '🚗', type: 'yesno', tab: 'General Details' },
  { key: 'cteContactPerson', label: 'CTE Contact Person', icon: '☎', type: 'text', tab: 'General Details' },
  { key: 'cteContactPersonPhone', label: 'CTE Contact Person Phone', icon: '☎', type: 'text', tab: 'General Details' },

  // ---- Registration ----
  { key: 'registrationNumber', label: 'Registration Number', icon: '🔖', type: 'text', tab: 'Registration' },
  { key: 'yearOfManufacturing', label: 'Year of Manufacturing', icon: '🚙', type: 'text', tab: 'Registration' },

  // ---- Insurance ----
  { key: 'insuranceProvider', label: 'Insurance Provider', icon: '🛡', type: 'text', tab: 'Insurance' },
  { key: 'insuranceValidUpto', label: 'Insurance Valid Upto', icon: '📅', type: 'date', tab: 'Insurance' },

  // ---- Other Details ----
  { key: 'hypothecation', label: 'Hypothecation', icon: '🏦', type: 'yesno', tab: 'Other Details' },
  { key: 'loanPaidOff', label: 'Has Loan Been Paid Off', icon: '💰', type: 'yesno', tab: 'Other Details' },
  { key: 'form35NocAvailable', label: 'Whether Valid Form 35 NOC Available', icon: '📄', type: 'yesno', tab: 'Other Details' },
  { key: 'listingRemarks', label: 'Listing Remarks', icon: '📋', type: 'text', tab: 'Other Details' },
  { key: 'faremeter', label: 'Faremeter', icon: '🚕', type: 'yesno', tab: 'Other Details' },
  { key: 'chassisNo', label: 'Chassis No', icon: '🔩', type: 'text', tab: 'Other Details' },
  { key: 'engineNo', label: 'Engine No', icon: '⚙', type: 'text', tab: 'Other Details' },

  // ---- Remarks ----
  { key: 'repoDate', label: 'Repo Date', icon: '📅', type: 'date', tab: 'Remarks' },
  { key: 'parkingRatePerDay', label: 'Parking Rate (per day)', icon: '🅿', type: 'number', tab: 'Remarks' },
  { key: 'additionalRemarks', label: 'Additional Remarks', icon: '📝', type: 'textarea', tab: 'Remarks' },
]

export const DETAIL_FIELD_BY_KEY: Record<string, DetailFieldDef> =
  Object.fromEntries(DETAIL_FIELDS.map((f) => [f.key, f]))

/**
 * Category names (case-insensitive) treated as "vehicles" — same fixed-name-set convention as
 * eventCategories.ts's EVENT_CATEGORY_SLUGS, mirrored on the backend in
 * AdminStockController.VEHICLE_CATEGORY_NAMES (the two must stay in sync; keep this list identical
 * there whenever it changes here).
 */
const VEHICLE_CATEGORY_NAMES = new Set(['vehicles', 'bank vehicles', 'auto', 'insurance'])

/** Keys of the 4 fields that become mandatory for a used (non-NEW) item in a vehicle category —
 *  every real repossessed/used vehicle has a registration, chassis, and yard; a brand-new one or a
 *  non-vehicle item (Electronics, Properties, ...) is exempt. Mirrors
 *  AdminStockController.requireVehicleDetails on the backend, which is what actually enforces it. */
export const REQUIRED_FOR_USED_VEHICLES = ['registrationNumber', 'chassisNo', 'yardName', 'yardLocation']

export function requiresVehicleDetails(categoryName: string, condition: string): boolean {
  return VEHICLE_CATEGORY_NAMES.has(categoryName.trim().toLowerCase()) && condition !== 'NEW'
}

/** Tabs collapsed into a single "Label: value per line" free-text box in the Add Stock form and
 *  bulk-Excel template, instead of one input/column per field — Remarks is left out since it has
 *  no mandatory fields and is already mostly free-text. Mirrored on the backend in
 *  AdminStockController.COLLAPSIBLE_TABS. */
export const COLLAPSIBLE_TABS: DetailTab[] = ['General Details', 'Registration', 'Insurance', 'Other Details']

/**
 * Parses a collapsed tab's free text (one "Label: value" pair per line) back into
 * `{ [DETAIL_FIELDS key]: value }` entries, matching each line's label against that tab's known
 * field labels (case-insensitive). A line whose label doesn't match anything known is kept, not
 * dropped — its raw (trimmed) label text becomes the attribute key, which DetailTabs.tsx's
 * existing fallback rendering already displays sensibly (under General Details, generic icon,
 * label = key) rather than silently losing whatever the admin typed.
 *
 * `excludeKeys` (normally REQUIRED_FOR_USED_VEHICLES) are skipped when matching so a line that
 * happens to type e.g. "Registration Number: ..." into the free-text box doesn't fight with the
 * dedicated mandatory-field input for the same key.
 */
export function parseDetailListText(tab: DetailTab, text: string, excludeKeys: string[]): Record<string, string> {
  const candidates = DETAIL_FIELDS.filter((f) => f.tab === tab && !excludeKeys.includes(f.key))
  const labelToKey = new Map(candidates.map((f) => [f.label.trim().toLowerCase(), f.key]))

  const result: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const idx = line.indexOf(':')
    if (idx === -1) continue
    const rawLabel = line.slice(0, idx).trim()
    const value = line.slice(idx + 1).trim()
    if (!rawLabel || !value) continue
    const key = labelToKey.get(rawLabel.toLowerCase()) ?? rawLabel
    result[key] = value
  }
  return result
}

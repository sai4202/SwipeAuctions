import type { Auction } from './api'

/**
 * Category-specific filters shown in the sidebar when a category is selected.
 * Filter sets are modelled on how mainstream marketplaces slice each category
 * (e.g. Cars24/OLX for Vehicles, Amazon/Flipkart for Electronics, MagicBricks/99acres
 * for Properties). Each filter reads either the auction's `brand` field or a value from
 * the listing's flexible `attributes` map (populated per category via ListingAttribute).
 */
export type FilterType = 'brand' | 'select' | 'minNumber'

export interface CategoryFilter {
  /** Attribute key (must match the backend ListingAttribute key), or 'brand' for the DTO brand field. */
  key: string
  label: string
  type: FilterType
  options?: string[] // for 'select'
  suffix?: string // for 'minNumber' inputs, e.g. "yr"
}

/** Keyed by lowercased category name. */
export const CATEGORY_FILTERS: Record<string, CategoryFilter[]> = {
  electronics: [
    { key: 'brand', label: 'Brand', type: 'brand' },
    { key: 'RAM', label: 'RAM', type: 'select', options: ['4 GB', '8 GB', '16 GB', '32 GB'] },
    { key: 'Storage', label: 'Storage', type: 'select', options: ['128 GB', '256 GB', '512 GB SSD', '1 TB SSD'] },
    { key: 'Screen size', label: 'Screen size', type: 'select', options: ['6.1"', '13.3"', '14"', '15.6"', '16"'] },
  ],
  vehicles: [
    { key: 'brand', label: 'Make', type: 'brand' },
    { key: 'Fuel', label: 'Fuel type', type: 'select', options: ['Petrol', 'Diesel', 'Electric', 'CNG', 'Hybrid'] },
    { key: 'Transmission', label: 'Transmission', type: 'select', options: ['Manual', 'Automatic'] },
    { key: 'Year', label: 'Year (from)', type: 'minNumber', suffix: 'yr' },
    { key: 'KM driven', label: 'Max KM driven', type: 'select', options: ['10000', '25000', '50000', '100000'] },
  ],
  properties: [
    { key: 'Bedrooms', label: 'Bedrooms', type: 'select', options: ['1 BHK', '2 BHK', '3 BHK', '4 BHK'] },
    { key: 'Furnishing', label: 'Furnishing', type: 'select', options: ['Furnished', 'Semi-furnished', 'Unfurnished'] },
    { key: 'Area (sqft)', label: 'Min area (sqft)', type: 'minNumber', suffix: 'sqft' },
  ],
  'bank vehicles': [
    { key: 'brand', label: 'Make', type: 'brand' },
    { key: 'Fuel', label: 'Fuel type', type: 'select', options: ['Petrol', 'Diesel', 'Electric', 'CNG', 'Hybrid'] },
    { key: 'Year', label: 'Year (from)', type: 'minNumber', suffix: 'yr' },
    { key: 'KM driven', label: 'Max KM driven', type: 'select', options: ['10000', '25000', '50000', '100000'] },
  ],
  // Insurance salvage lots on this platform are always vehicles, so it shares Bank Vehicles' filter set.
  insurance: [
    { key: 'brand', label: 'Make', type: 'brand' },
    { key: 'Fuel', label: 'Fuel type', type: 'select', options: ['Petrol', 'Diesel', 'Electric', 'CNG', 'Hybrid'] },
    { key: 'Year', label: 'Year (from)', type: 'minNumber', suffix: 'yr' },
    { key: 'KM driven', label: 'Max KM driven', type: 'select', options: ['10000', '25000', '50000', '100000'] },
  ],
  // Premium / Auto are vehicle-auction categories (mirrors CarTrade Exchange's event pill bar), same
  // filter shape as Bank Vehicles.
  premium: [
    { key: 'brand', label: 'Make', type: 'brand' },
    { key: 'Fuel', label: 'Fuel type', type: 'select', options: ['Petrol', 'Diesel', 'Electric', 'CNG', 'Hybrid'] },
    { key: 'Year', label: 'Year (from)', type: 'minNumber', suffix: 'yr' },
    { key: 'KM driven', label: 'Max KM driven', type: 'select', options: ['10000', '25000', '50000', '100000'] },
  ],
  auto: [
    { key: 'brand', label: 'Make', type: 'brand' },
    { key: 'Fuel', label: 'Fuel type', type: 'select', options: ['Petrol', 'Diesel', 'Electric', 'CNG', 'Hybrid'] },
    { key: 'Year', label: 'Year (from)', type: 'minNumber', suffix: 'yr' },
    { key: 'KM driven', label: 'Max KM driven', type: 'select', options: ['10000', '25000', '50000', '100000'] },
  ],
}

/**
 * Vehicle Type is a checkbox (multi-select) filter shown on the Events browse view, on top of the
 * per-category filters above — modelled on CarTrade Exchange's "Vehicle Type" filter bar.
 */
export const VEHICLE_TYPE_KEY = 'Vehicle Type'
export const VEHICLE_TYPE_OPTIONS = ['4W', 'CV', '2W', 'TR/FE', '3W', 'CE']

/** Non-brand spec fields for a category, used to build the dynamic attributes form when listing a new item. */
export function attributeFieldsForCategory(categoryName: string): CategoryFilter[] {
  return (CATEGORY_FILTERS[categoryName.toLowerCase()] ?? []).filter((f) => f.type !== 'brand')
}

/** URL param key for a category filter (namespaced to avoid clashing with global filters). */
export function filterParam(f: CategoryFilter): string {
  return 'a_' + f.key.toLowerCase().replace(/[^a-z0-9]+/g, '_')
}

/** Digits from a value like "45,000 km" → 45000; NaN if none. */
function toNumber(v: string | undefined): number {
  if (!v) return NaN
  return parseInt(v.replace(/[^0-9]/g, ''), 10)
}

/** Does an auction pass a single category filter set to `value`? */
export function matchesFilter(a: Auction, f: CategoryFilter, value: string): boolean {
  if (!value) return true
  if (f.type === 'brand') return (a.brand ?? '').toLowerCase() === value.toLowerCase()
  if (f.type === 'minNumber') {
    const n = toNumber(a.attributes?.[f.key])
    return !isNaN(n) && n >= Number(value)
  }
  // 'select'
  if (f.key === 'KM driven') {
    const n = toNumber(a.attributes?.[f.key]) // "Max KM driven": keep vehicles at/under the cap
    return !isNaN(n) && n <= Number(value)
  }
  return (a.attributes?.[f.key] ?? '').toLowerCase() === value.toLowerCase()
}

import { describe, expect, it } from 'vitest'
import { filterParam, matchesFilter, type BrowseFilter } from '../browseFilters'
import type { Auction } from '../api'

function auction(overrides: Partial<Auction> = {}): Auction {
  return {
    id: 'a1', listingId: 'l1', title: 'Test item', basePrice: 100, currentHighestBid: null,
    status: 'OPEN', startTime: '2026-01-01T00:00:00', currentEndTime: '2026-01-02T00:00:00',
    bidCount: 0, categoryName: 'Vehicles', categoryId: 'c1', brand: null, condition: 'USED',
    city: null, state: null, zip: null, coverImageUrl: null, images: [], yourBid: null,
    attributes: {}, isWinner: false, settlementPaid: false, eventId: null, eventName: null,
    sellerEmail: 'seller@swipeauctions.test', swipeStock: false, requiredTier: 'NONE', registered: false,
    ...overrides,
  }
}

describe('filterParam', () => {
  it('lowercases and namespaces a simple key', () => {
    expect(filterParam('Brand')).toBe('a_brand')
  })

  it('collapses non-alphanumeric runs into a single underscore', () => {
    expect(filterParam('KM driven')).toBe('a_km_driven')
    expect(filterParam('Year (from)')).toBe('a_year_from_')
  })
})

describe('matchesFilter', () => {
  it('an empty filter value always matches', () => {
    const f: BrowseFilter = { key: 'brand', label: 'Brand', type: 'brand', options: [] }
    expect(matchesFilter(auction(), f, '')).toBe(true)
  })

  describe('brand filter', () => {
    const f: BrowseFilter = { key: 'brand', label: 'Brand', type: 'brand', options: [] }

    it('matches case-insensitively', () => {
      expect(matchesFilter(auction({ brand: 'Toyota' }), f, 'toyota')).toBe(true)
    })

    it('does not match a different brand', () => {
      expect(matchesFilter(auction({ brand: 'Toyota' }), f, 'Honda')).toBe(false)
    })

    it('does not match when the auction has no brand', () => {
      expect(matchesFilter(auction({ brand: null }), f, 'Toyota')).toBe(false)
    })
  })

  describe('ENUM (generic attribute) filter', () => {
    const f: BrowseFilter = { key: 'Fuel Type', label: 'Fuel Type', type: 'ENUM', options: [] }

    it('matches case-insensitively on the attribute value', () => {
      expect(matchesFilter(auction({ attributes: { 'Fuel Type': 'Diesel' } }), f, 'diesel')).toBe(true)
    })

    it('does not match a missing attribute', () => {
      expect(matchesFilter(auction({ attributes: {} }), f, 'Diesel')).toBe(false)
    })
  })

  describe('NUMBER filter — default "at least" semantics', () => {
    const f: BrowseFilter = { key: 'Year', label: 'Year (from)', type: 'NUMBER', options: [] }

    it('matches when the attribute value is at least the filter value', () => {
      expect(matchesFilter(auction({ attributes: { Year: '2021' } }), f, '2020')).toBe(true)
    })

    it('does not match when the attribute value is below the filter value', () => {
      expect(matchesFilter(auction({ attributes: { Year: '2018' } }), f, '2020')).toBe(false)
    })

    it('does not match when the attribute is missing or non-numeric', () => {
      expect(matchesFilter(auction({ attributes: {} }), f, '2020')).toBe(false)
    })
  })

  describe('NUMBER filter — "KM driven" at-most special case', () => {
    const f: BrowseFilter = { key: 'KM driven', label: 'KM driven', type: 'NUMBER', options: [] }

    it('matches when the attribute value is at most the filter value', () => {
      expect(matchesFilter(auction({ attributes: { 'KM driven': '45,000 km' } }), f, '50000')).toBe(true)
    })

    it('does not match when the attribute value exceeds the filter value', () => {
      expect(matchesFilter(auction({ attributes: { 'KM driven': '80,000 km' } }), f, '50000')).toBe(false)
    })

    it('parses digits out of a formatted string with units', () => {
      expect(matchesFilter(auction({ attributes: { 'KM driven': '12,345 km' } }), f, '12345')).toBe(true)
    })
  })
})

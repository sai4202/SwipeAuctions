import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  getAdminCategories, createStockListing, uploadStockImage, createStockAuction, bulkImportStock, downloadStockTemplate,
  errorMessage, type AdminCategory, type BulkImportResult,
} from '../api'
import { DETAIL_FIELDS, DETAIL_TABS, COLLAPSIBLE_TABS, REQUIRED_FOR_USED_VEHICLES, requiresVehicleDetails, parseDetailListText, type DetailFieldDef } from '../detailFields'
import { VEHICLE_TYPE_OPTIONS } from '../catalogFilters'
import { EVENT_CATEGORIES } from '../eventCategories'

/** Categories that use the Vehicle Type filter (see catalogFilters.ts) — same set as the events
 *  browsing UI (EVENT_CATEGORIES), by lowercased category name for a simple string comparison here. */
const VEHICLE_TYPE_CATEGORY_NAMES = new Set(Object.values(EVENT_CATEGORIES).map((c) => c.label.toLowerCase()))

const CONDITIONS = ['NEW', 'USED', 'REFURBISHED', 'FOR_PARTS']
const NEW_CATEGORY_VALUE = '__new__'

/**
 * "+ Add Stock": admin creates inventory attributed to the Swipe Stock platform seller account —
 * one item at a time, or many at once via an Excel file. Every item shows up on the normal
 * Auctions browse regardless; the "List on Swipe Stock page" checkbox (or the sheet's "Swipe Stock"
 * column) is what additionally surfaces it on /swipe-stock.
 */
export default function AddStockModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [mode, setMode] = useState<'single' | 'bulk'>('single')
  const [categories, setCategories] = useState<AdminCategory[]>([])

  useEffect(() => { getAdminCategories().then(setCategories).catch(() => {}) }, [])

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 640 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>+ Add Stock</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <div className="tabs">
            <button type="button" className={'tab' + (mode === 'single' ? ' active' : '')} onClick={() => setMode('single')}>Single Item</button>
            <button type="button" className={'tab' + (mode === 'bulk' ? ' active' : '')} onClick={() => setMode('bulk')}>Bulk Upload (Excel)</button>
          </div>
          {mode === 'single'
            ? <AddStockSingleForm categories={categories} onCategoriesChanged={setCategories} onCreated={onCreated} />
            : <AddStockBulkForm onCreated={onCreated} />}
        </div>
      </div>
    </div>
  )
}

/** One admin-form input for a single detailFields.ts entry, rendering the control that matches
 *  its `type` (Yes/No fields get a 3-state select — blank means "not entered", not "No"). `required`
 *  is a UI hint only (asterisk + native required attribute) — the backend
 *  (AdminStockController.requireVehicleDetails) is what actually enforces it. */
function DetailFieldInput({ field, value, onChange, required }: {
  field: DetailFieldDef; value: string; onChange: (v: string) => void; required?: boolean
}) {
  const wrap = (control: ReactNode) => (
    <div className="fgroup" style={{ flex: 1, minWidth: field.type === 'textarea' ? '100%' : 180 }}>
      <small>{field.icon} {field.label}{required && <span style={{ color: 'var(--red)' }}> *</span>}</small>
      {control}
    </div>
  )
  if (field.type === 'yesno') {
    return wrap(
      <select value={value} onChange={(e) => onChange(e.target.value)} required={required}>
        <option value="">—</option>
        <option value="Yes">Yes</option>
        <option value="No">No</option>
      </select>,
    )
  }
  if (field.type === 'textarea') {
    return wrap(
      <textarea style={{ width: '100%', minHeight: 60, fontFamily: 'inherit' }} value={value} onChange={(e) => onChange(e.target.value)} required={required} />,
    )
  }
  return wrap(
    <input type={field.type === 'date' ? 'date' : field.type === 'number' ? 'number' : 'text'}
           min={field.type === 'number' ? 0 : undefined}
           value={value} onChange={(e) => onChange(e.target.value)} required={required} />,
  )
}

function AddStockSingleForm({ categories, onCategoriesChanged, onCreated }: {
  categories: AdminCategory[]
  onCategoriesChanged: (cats: AdminCategory[]) => void
  onCreated: () => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [newCategoryName, setNewCategoryName] = useState('')
  const [brand, setBrand] = useState('')
  const [condition, setCondition] = useState('USED')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [zip, setZip] = useState('')
  const [reservePrice, setReservePrice] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [swipeStock, setSwipeStock] = useState(false)
  const [requiredTier, setRequiredTier] = useState<'NONE' | 'SILVER' | 'GOLD' | 'DIAMOND'>('NONE')
  const [files, setFiles] = useState<File[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  // Structured item-detail fields (Yard Name, Registration Number, Chassis No, ...) shown as tabs
  // on the detail page — see detailFields.ts, the single source of truth shared with DetailTabs.tsx.
  const [detailValues, setDetailValues] = useState<Record<string, string>>({})
  const setDetailValue = (key: string, value: string) => setDetailValues((v) => ({ ...v, [key]: value }))
  // Non-mandatory fields for the 4 COLLAPSIBLE_TABS are entered as one "Label: value per line" free
  // text box per tab instead of one input per field — parsed back into individual attribute keys on
  // submit via parseDetailListText, so everything downstream (storage, the detail page) is unaware
  // this collapsed box exists at all.
  const [tabListText, setTabListText] = useState<Record<string, string>>({})
  // Vehicle Type (4W/CV/2W/TR-FE/3W/CE) — the events browse UI's Vehicle Type filter reads this exact
  // attribute key. Kept separate from detailValues since it's category-conditional and a fixed
  // dropdown, not one of the generic detail-tab fields.
  const [vehicleType, setVehicleType] = useState('')

  // Every real used/repossessed vehicle has a registration, chassis, and yard — see detailFields.ts.
  // UI hint only; AdminStockController.requireVehicleDetails is what actually enforces this.
  const selectedCategoryName = categoryId === NEW_CATEGORY_VALUE
    ? newCategoryName
    : (categories.find((c) => c.id === categoryId)?.name ?? '')
  const vehicleDetailsRequired = requiresVehicleDetails(selectedCategoryName, condition)
  const showVehicleType = VEHICLE_TYPE_CATEGORY_NAMES.has(selectedCategoryName.trim().toLowerCase())

  const reset = () => {
    setTitle(''); setDescription(''); setBrand(''); setCity(''); setState(''); setZip('')
    setReservePrice(''); setStartTime(''); setEndTime(''); setSwipeStock(false); setRequiredTier('NONE'); setFiles([])
    setCategoryId(''); setNewCategoryName(''); setDetailValues({}); setTabListText({}); setVehicleType('')
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError(''); setSuccess('')
    try {
      const usingNewCategory = categoryId === NEW_CATEGORY_VALUE
      if (usingNewCategory && !newCategoryName.trim()) throw new Error('Enter a name for the new category')

      // Only send fields the admin actually filled in — an empty string would otherwise store a
      // blank ListingAttribute row, which DetailTabs would then have to filter out again anyway.
      const attributes = Object.fromEntries(
        Object.entries(detailValues).filter(([, v]) => v.trim() !== ''),
      )
      for (const tab of COLLAPSIBLE_TABS) {
        Object.assign(attributes, parseDetailListText(tab, tabListText[tab] ?? '', REQUIRED_FOR_USED_VEHICLES))
      }
      if (showVehicleType && vehicleType) attributes['Vehicle Type'] = vehicleType

      const listing = await createStockListing({
        title,
        description: description || undefined,
        categoryId: usingNewCategory ? undefined : categoryId || undefined,
        categoryName: usingNewCategory ? newCategoryName.trim() : undefined,
        brand: brand || undefined,
        condition,
        city: city || undefined,
        state: state || undefined,
        zip: zip || undefined,
        reservePrice: Number(reservePrice),
        attributes: Object.keys(attributes).length > 0 ? attributes : undefined,
        swipeStock,
        requiredTier,
      })

      for (let i = 0; i < files.length; i++) {
        await uploadStockImage(listing.id, files[i], i === 0)
      }

      await createStockAuction(listing.id, {
        basePrice: Number(reservePrice),
        // <input type="datetime-local"> already yields "YYYY-MM-DDTHH:mm" local wall-clock time,
        // which is exactly what the backend's LocalDateTime expects — no Date/timezone conversion.
        startTime: startTime || null,
        endTime: endTime || null,
      })

      if (usingNewCategory) {
        getAdminCategories().then(onCategoriesChanged).catch(() => {})
      }
      setSuccess(`"${title}" created${swipeStock ? ' — listed on Swipe Stock' : ''}.`)
      reset()
      onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {error && <div className="error">{error}</div>}
      {success && <div className="ok">{success}</div>}

      <div className="fgroup">
        <small>Title</small>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </div>
      <div className="fgroup">
        <small>Description</small>
        <input value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 180 }}>
          <small>Category</small>
          <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
            <option value="">Select…</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            <option value={NEW_CATEGORY_VALUE}>＋ New category…</option>
          </select>
        </div>
        {categoryId === NEW_CATEGORY_VALUE && (
          <div className="fgroup" style={{ flex: 1, minWidth: 180 }}>
            <small>New category name</small>
            <input value={newCategoryName} onChange={(e) => setNewCategoryName(e.target.value)} placeholder="e.g. Furniture" required />
          </div>
        )}
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Brand</small>
          <input value={brand} onChange={(e) => setBrand(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Condition</small>
          <select value={condition} onChange={(e) => setCondition(e.target.value)}>
            {CONDITIONS.map((c) => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
          </select>
        </div>
        {showVehicleType && (
          <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
            <small>Vehicle Type</small>
            <select value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}>
              <option value="">Select…</option>
              {VEHICLE_TYPE_OPTIONS.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
        )}
      </div>
      {showVehicleType && (
        <p className="muted" style={{ fontSize: 12.5, margin: 0 }}>
          One vehicle type per item. A branch or salvage lot with both 4-wheelers and 2-wheelers needs
          two separate items/auctions — don't mix types under a single listing.
        </p>
      )}

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 120 }}>
          <small>City</small>
          <input value={city} onChange={(e) => setCity(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 100 }}>
          <small>State</small>
          <input value={state} onChange={(e) => setState(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 100 }}>
          <small>Zip</small>
          <input value={zip} onChange={(e) => setZip(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Base price (₹)</small>
          <input type="number" value={reservePrice} onChange={(e) => setReservePrice(e.target.value)} required min={0} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
          <small>Start time (optional, default now)</small>
          <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
          <small>End time (optional, default +3 days)</small>
          <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
        </div>
      </div>

      <div className="fgroup">
        <small>Images (first one becomes the cover)</small>
        <input type="file" accept="image/*" multiple onChange={(e) => setFiles(Array.from(e.target.files ?? []))} />
      </div>

      {/* Structured item-detail fields — shown as tabs (General Details / Registration / Insurance /
          Other Details / Remarks) on the item's detail page. Every field here is optional — except
          Registration Number / Chassis No / Yard Name / Yard Location, marked with * below, once the
          category+condition combo makes them mandatory (see requiresVehicleDetails in detailFields.ts). */}
      {vehicleDetailsRequired && (
        <p className="muted" style={{ fontSize: 12.5, margin: 0 }}>
          <span style={{ color: 'var(--red)' }}>*</span> Required for a used {selectedCategoryName} item — every real
          repossessed/used vehicle has these. Only exempt when Condition is set to NEW.
        </p>
      )}
      {DETAIL_TABS.map((tab) => {
        const isCollapsible = COLLAPSIBLE_TABS.includes(tab)
        const tabFields = DETAIL_FIELDS.filter((f) => f.tab === tab)
        const mandatoryFields = isCollapsible ? tabFields.filter((f) => REQUIRED_FOR_USED_VEHICLES.includes(f.key)) : []
        const freeFields = isCollapsible ? tabFields.filter((f) => !REQUIRED_FOR_USED_VEHICLES.includes(f.key)) : tabFields
        return (
          <div key={tab} className="detail-field-group">
            <div className="cat-filters-head">{tab}</div>
            {mandatoryFields.length > 0 && (
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: isCollapsible ? 10 : 0 }}>
                {mandatoryFields.map((f) => (
                  <DetailFieldInput key={f.key} field={f} value={detailValues[f.key] ?? ''} onChange={(v) => setDetailValue(f.key, v)}
                                     required={vehicleDetailsRequired} />
                ))}
              </div>
            )}
            {isCollapsible ? (
              <div className="fgroup">
                <small>
                  {tab} — one "Label: value" per line{freeFields.length > 0 && ` (e.g. ${freeFields[0].label}: ...)`}
                </small>
                <textarea
                  style={{ width: '100%', minHeight: 80, fontFamily: 'inherit' }}
                  value={tabListText[tab] ?? ''}
                  onChange={(e) => setTabListText((v) => ({ ...v, [tab]: e.target.value }))}
                  placeholder={freeFields.map((f) => `${f.label}: `).join('\n')}
                />
              </div>
            ) : (
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {freeFields.map((f) => (
                  <DetailFieldInput key={f.key} field={f} value={detailValues[f.key] ?? ''} onChange={(v) => setDetailValue(f.key, v)} />
                ))}
              </div>
            )}
          </div>
        )
      })}

      <div className="fgroup" style={{ maxWidth: 220 }}>
        <small>Required subscription tier</small>
        <select value={requiredTier} onChange={(e) => setRequiredTier(e.target.value as typeof requiredTier)}>
          <option value="NONE">None — visible to everyone</option>
          <option value="SILVER">Silver</option>
          <option value="GOLD">Gold</option>
          <option value="DIAMOND">Diamond</option>
        </select>
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5 }}>
        <input type="checkbox" checked={swipeStock} onChange={(e) => setSwipeStock(e.target.checked)} />
        List on the Swipe Stock page
      </label>

      <button type="submit" className="btn" disabled={saving}>{saving ? 'Creating…' : 'Create item'}</button>
    </form>
  )
}

function AddStockBulkForm({ onCreated }: { onCreated: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [swipeStock, setSwipeStock] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<BulkImportResult | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!file) return
    setSaving(true); setError(''); setResult(null)
    try {
      const res = await bulkImportStock(file, swipeStock)
      setResult(res)
      if (res.created > 0) onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <p className="muted" style={{ fontSize: 13 }}>
        Upload an .xlsx/.xls file with columns: <b>Title</b>, <b>Category</b>, Brand, Condition, City, State,
        Zip, <b>Base Price</b>, Start Time, End Time, Swipe Stock, Required Tier. Only Title, Category and
        Base Price are required — a category that doesn't exist yet is created automatically, and a blank
        Required Tier defaults to no subscription gate. A bad row is skipped, not the whole file.
      </p>
      <button type="button" className="linkbtn" onClick={() => downloadStockTemplate()}>⬇ Download template</button>

      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {error && <div className="error">{error}</div>}
        <div className="fgroup">
          <small>Excel file</small>
          <input type="file" accept=".xlsx,.xls" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5 }}>
          <input type="checkbox" checked={swipeStock} onChange={(e) => setSwipeStock(e.target.checked)} />
          List all rows on the Swipe Stock page (unless a row's own "Swipe Stock" column says otherwise)
        </label>
        <button type="submit" className="btn" disabled={saving || !file}>{saving ? 'Importing…' : 'Import'}</button>
      </form>

      {result && (
        <div>
          <p className="ok">Imported {result.created} of {result.totalRows} row(s).</p>
          {result.errors.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="admin-table">
                <thead><tr><th>Row</th><th>Error</th></tr></thead>
                <tbody>
                  {result.errors.map((e) => <tr key={e.row}><td>{e.row}</td><td>{e.message}</td></tr>)}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

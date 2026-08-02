import { useEffect, useState, type FormEvent } from 'react'
import {
  getAdminBanners, createBanner, toggleBannerActive, deleteBanner, getAdminCategories, errorMessage,
  type AdminBanner, type AdminCategory,
} from '../../api'
import { AdminPageHeader } from './shared'

/** Reference site's "Banners" section — promotional image slides, optionally scoped to a category,
 *  shown on the public homepage (see BannerStrip.tsx). Purely additive/marketing content. */
export default function AdminBannersPage() {
  const [banners, setBanners] = useState<AdminBanner[]>([])
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [error, setError] = useState('')
  const [showAdd, setShowAdd] = useState(false)

  const load = () => { getAdminBanners().then(setBanners).catch((e) => setError(errorMessage(e))) }
  useEffect(load, [])
  useEffect(() => { getAdminCategories().then(setCategories).catch(() => {}) }, [])

  const toggle = async (b: AdminBanner) => {
    setError('')
    try {
      const updated = await toggleBannerActive(b.id, !b.active)
      setBanners((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) }
  }

  const remove = async (b: AdminBanner) => {
    if (!confirm('Remove this banner?')) return
    setError('')
    try {
      await deleteBanner(b.id)
      setBanners((prev) => prev.filter((x) => x.id !== b.id))
    } catch (e) { setError(errorMessage(e)) }
  }

  return (
    <div>
      <AdminPageHeader section="Banners" title="Category Banners" subtitle="Promotional image slides shown on the homepage."
                        actions={<button type="button" className="btn sm" onClick={() => setShowAdd(true)}>+ Add New Banner</button>} />
      {error && <div className="error">{error}</div>}

      <div className="chart-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))' }}>
        {banners.map((b) => (
          <div key={b.id} className="card" style={{ padding: 0, overflow: 'hidden' }}>
            <img src={b.imageUrl} alt={b.title ?? 'Banner'} style={{ width: '100%', height: 130, objectFit: 'cover', display: 'block' }} />
            <div style={{ padding: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <b style={{ fontSize: 13 }}>{b.title ?? b.categoryName ?? 'Platform-wide'}</b>
                <span className={`badge ${b.active ? 'ACTIVE' : 'WITHDRAWN'}`}>{b.active ? 'Active' : 'Inactive'}</span>
              </div>
              <p className="muted" style={{ fontSize: 12, margin: '0 0 10px' }}>
                {b.categoryName ?? 'All categories'}{b.linkUrl ? ` · links to ${b.linkUrl}` : ''}
              </p>
              <div style={{ display: 'flex', gap: 8 }}>
                <button type="button" className="btn ghost sm" onClick={() => toggle(b)}>{b.active ? 'Deactivate' : 'Activate'}</button>
                <button type="button" className="btn ghost sm" onClick={() => remove(b)}>Delete</button>
              </div>
            </div>
          </div>
        ))}
        {banners.length === 0 && <p className="muted">No banners yet.</p>}
      </div>

      {showAdd && (
        <AddBannerModal categories={categories} onClose={() => setShowAdd(false)}
                         onCreated={(b) => { setBanners((prev) => [...prev, b]); setShowAdd(false) }} />
      )}
    </div>
  )
}

function AddBannerModal({ categories, onClose, onCreated }: {
  categories: AdminCategory[]; onClose: () => void; onCreated: (b: AdminBanner) => void
}) {
  const [image, setImage] = useState<File | null>(null)
  const [categoryId, setCategoryId] = useState('')
  const [linkUrl, setLinkUrl] = useState('')
  const [title, setTitle] = useState('')
  const [sortOrder, setSortOrder] = useState('0')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!image) return
    setSaving(true); setError('')
    try {
      const created = await createBanner({
        image, categoryId: categoryId || undefined, linkUrl: linkUrl || undefined,
        title: title || undefined, sortOrder: Number(sortOrder),
      })
      onCreated(created)
    } catch (e2) { setError(errorMessage(e2)) } finally { setSaving(false) }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Add New Banner</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {error && <div className="error">{error}</div>}
            <div className="fgroup">
              <small>Image</small>
              <input type="file" accept="image/*" onChange={(e) => setImage(e.target.files?.[0] ?? null)} required />
            </div>
            <div className="fgroup">
              <small>Title (optional)</small>
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Festive Sale" />
            </div>
            <div className="fgroup">
              <small>Category (optional — leave blank for platform-wide)</small>
              <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">All categories</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="fgroup">
              <small>Link URL (optional)</small>
              <input value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} placeholder="/auctions" />
            </div>
            <div className="fgroup" style={{ maxWidth: 140 }}>
              <small>Sort order</small>
              <input type="number" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} />
            </div>
            <button type="submit" className="btn" disabled={saving || !image}>{saving ? 'Adding…' : 'Add banner'}</button>
          </form>
        </div>
      </div>
    </div>
  )
}

import { useEffect, useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router-dom'
import {
  getAdminCategories, createAdminCategory, getAdminCategoryAttributes, createAdminCategoryAttribute, errorMessage,
  type AdminCategory, type AdminCategoryAttribute,
} from '../../api'
import { AdminPageHeader } from './shared'
import type { AdminOutletContext } from '../../components/AdminLayout'

const VALUE_TYPES: AdminCategoryAttribute['valueType'][] = ['TEXT', 'NUMBER', 'BOOLEAN', 'ENUM']

export default function AdminCategoriesPage() {
  const { refreshTick } = useOutletContext<AdminOutletContext>()
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [parentId, setParentId] = useState('')
  const [selected, setSelected] = useState<AdminCategory | null>(null)
  const [attributes, setAttributes] = useState<AdminCategoryAttribute[]>([])
  const [attrError, setAttrError] = useState('')
  const [attrSaving, setAttrSaving] = useState(false)
  const [attrKey, setAttrKey] = useState('')
  const [attrLabel, setAttrLabel] = useState('')
  const [attrType, setAttrType] = useState<AdminCategoryAttribute['valueType']>('TEXT')
  const [attrFilterable, setAttrFilterable] = useState(true)

  const load = () => { getAdminCategories().then(setCategories).catch((e) => setError(errorMessage(e))) }
  useEffect(load, [refreshTick])

  const openCategory = (c: AdminCategory) => {
    setSelected(c); setAttrError(''); setAttributes([])
    getAdminCategoryAttributes(c.id).then(setAttributes).catch((e) => setAttrError(errorMessage(e)))
  }

  const submitCategory = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      const created = await createAdminCategory({ name, slug, parentId: parentId || null })
      setCategories((prev) => [...prev, created])
      setName(''); setSlug(''); setParentId('')
    } catch (e) { setError(errorMessage(e)) } finally { setSaving(false) }
  }

  const submitAttribute = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected) return
    setAttrSaving(true); setAttrError('')
    try {
      const created = await createAdminCategoryAttribute(selected.id, {
        key: attrKey, label: attrLabel, valueType: attrType, filterable: attrFilterable,
      })
      setAttributes((prev) => [...prev, created])
      setAttrKey(''); setAttrLabel(''); setAttrType('TEXT'); setAttrFilterable(true)
    } catch (e) { setAttrError(errorMessage(e)) } finally { setAttrSaving(false) }
  }

  return (
    <div>
      <AdminPageHeader section="Categories" title="Categories" subtitle="Catalogue taxonomy and per-category spec/filter attributes." />
      <div className="card">
        <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Add a category</h2>
        <form onSubmit={submitCategory} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div className="fgroup">
            <small>Name</small>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Furniture" required />
          </div>
          <div className="fgroup">
            <small>Slug</small>
            <input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="e.g. furniture" required />
          </div>
          <div className="fgroup">
            <small>Parent (optional)</small>
            <select value={parentId} onChange={(e) => setParentId(e.target.value)}>
              <option value="">None</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <button type="submit" className="btn sm" disabled={saving}>{saving ? 'Adding…' : 'Add category'}</button>
        </form>
        {error && <div className="error">{error}</div>}

        <div style={{ overflowX: 'auto', marginTop: 18 }}>
          <table className="admin-table">
            <thead><tr><th>Name</th><th>Slug</th><th>Parent</th><th></th></tr></thead>
            <tbody>
              {categories.map((c) => (
                <tr key={c.id} onClick={() => openCategory(c)} style={{ cursor: 'pointer' }}>
                  <td>{c.name}</td>
                  <td>{c.slug}</td>
                  <td>{categories.find((p) => p.id === c.parentId)?.name ?? '—'}</td>
                  <td className="muted">Manage attributes →</td>
                </tr>
              ))}
              {categories.length === 0 && <tr><td colSpan={4} className="muted">No categories yet.</td></tr>}
            </tbody>
          </table>
        </div>

        {selected && (
          <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
            <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.name} — attributes</h2>
            <p className="muted" style={{ fontSize: 12.5 }}>
              Attributes become spec fields for listings in this category, and filterable ones show up as browse filters.
            </p>
            {attrError && <div className="error">{attrError}</div>}
            <div style={{ overflowX: 'auto' }}>
              <table className="admin-table">
                <thead><tr><th>Key</th><th>Label</th><th>Type</th><th>Filterable</th></tr></thead>
                <tbody>
                  {attributes.map((a) => (
                    <tr key={a.id}>
                      <td>{a.key}</td><td>{a.label}</td><td>{a.valueType}</td><td>{a.filterable ? 'Yes' : 'No'}</td>
                    </tr>
                  ))}
                  {attributes.length === 0 && <tr><td colSpan={4} className="muted">No attributes yet.</td></tr>}
                </tbody>
              </table>
            </div>
            <form onSubmit={submitAttribute} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end', marginTop: 12 }}>
              <div className="fgroup">
                <small>Key</small>
                <input value={attrKey} onChange={(e) => setAttrKey(e.target.value)} placeholder="e.g. Material" required />
              </div>
              <div className="fgroup">
                <small>Label</small>
                <input value={attrLabel} onChange={(e) => setAttrLabel(e.target.value)} placeholder="e.g. Material" required />
              </div>
              <div className="fgroup">
                <small>Type</small>
                <select value={attrType} onChange={(e) => setAttrType(e.target.value as AdminCategoryAttribute['valueType'])}>
                  {VALUE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                <input type="checkbox" checked={attrFilterable} onChange={(e) => setAttrFilterable(e.target.checked)} />
                Filterable
              </label>
              <button type="submit" className="btn ghost sm" disabled={attrSaving}>{attrSaving ? 'Adding…' : 'Add attribute'}</button>
            </form>
          </div>
        )}
      </div>
    </div>
  )
}

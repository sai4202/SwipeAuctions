import { createContext, useCallback, useContext, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'

interface Rect { x: number; y: number; width: number; height: number }

interface TabState extends Rect {
  id: number
  path: string
  z: number
  maximized: boolean
  prevRect: Rect | null
}

interface FloatingTabsState {
  tabCount: number
  maxTabs: number
  addTab: (path?: string) => void
}

const FloatingTabsContext = createContext<FloatingTabsState | undefined>(undefined)

const MAX_TABS = 4
const MIN_WIDTH = 260
const MIN_HEIGHT = 200
const MAXIMIZE_MARGIN = 16
const TILE_MARGIN = 10
const TILE_GAP = 10

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), Math.max(min, max))
}

/**
 * Lays out every open tab: a single tab gets a roomy default size (comfortably above the app's own
 * 900px mobile-nav breakpoint, so it shows the real desktop header, not the hamburger fallback);
 * two or more tabs auto-tile into that many equal-width columns spanning the full window height —
 * 2 tabs split the screen in half, 3 into equal thirds, 4 into equal quarters, etc. Re-run whenever
 * a tab is added or closed; any manual drag/resize in between is preserved until the next such event.
 */
function retile(current: TabState[]): TabState[] {
  const n = current.length
  if (n === 0) return current
  const vw = window.innerWidth
  const vh = window.innerHeight

  if (n === 1) {
    const isMobile = vw < 760
    let width: number
    let height: number
    if (isMobile) {
      width = Math.max(vw - 24, MIN_WIDTH)
      height = Math.min(vh - 140, 480)
    } else {
      width = Math.min(Math.max(Math.round(vw * 0.55), 960), vw - 40)
      height = Math.min(Math.max(Math.round(vh * 0.62), 600), vh - 80)
    }
    const x = clamp(80, 8, Math.max(8, vw - width - 8))
    const y = clamp(60, 8, Math.max(8, vh - height - 8))
    return [{ ...current[0], x, y, width, height, maximized: false, prevRect: null }]
  }

  const colWidth = Math.max(MIN_WIDTH, Math.floor((vw - TILE_MARGIN * 2 - TILE_GAP * (n - 1)) / n))
  const height = Math.max(MIN_HEIGHT, vh - TILE_MARGIN * 2)
  return current.map((t, i) => ({
    ...t,
    x: TILE_MARGIN + i * (colWidth + TILE_GAP),
    y: TILE_MARGIN,
    width: colWidth,
    height,
    maximized: false,
    prevRect: null,
  }))
}

export function FloatingTabsProvider({ children }: { children: ReactNode }) {
  const [tabs, setTabs] = useState<TabState[]>([])
  const nextId = useRef(1)
  const nextZ = useRef(1)

  const addTab = useCallback((path = '/auctions') => {
    setTabs((t) => {
      if (t.length >= MAX_TABS) return t
      const newTab: TabState = {
        id: nextId.current++, path, x: 0, y: 0, width: MIN_WIDTH, height: MIN_HEIGHT,
        z: nextZ.current++, maximized: false, prevRect: null,
      }
      return retile([...t, newTab])
    })
  }, [])

  const closeTab = useCallback((id: number) => {
    setTabs((t) => retile(t.filter((x) => x.id !== id)))
  }, [])
  const focusTab = useCallback((id: number) => {
    setTabs((t) => t.map((x) => (x.id === id ? { ...x, z: nextZ.current++ } : x)))
  }, [])
  const updateTab = useCallback((id: number, patch: Partial<TabState>) => {
    setTabs((t) => t.map((x) => (x.id === id ? { ...x, ...patch } : x)))
  }, [])
  const toggleMaximize = useCallback((id: number) => {
    setTabs((t) => t.map((x) => {
      if (x.id !== id) return x
      if (x.maximized) {
        const r = x.prevRect ?? { x: 80, y: 100, width: 420, height: 480 }
        return { ...x, ...r, maximized: false, prevRect: null }
      }
      return {
        ...x,
        prevRect: { x: x.x, y: x.y, width: x.width, height: x.height },
        x: MAXIMIZE_MARGIN, y: MAXIMIZE_MARGIN,
        width: window.innerWidth - MAXIMIZE_MARGIN * 2,
        height: window.innerHeight - MAXIMIZE_MARGIN * 2,
        maximized: true,
      }
    }))
  }, [])

  return (
    <FloatingTabsContext.Provider value={{ tabCount: tabs.length, maxTabs: MAX_TABS, addTab }}>
      {children}
      {tabs.map((tab) => (
        <FloatingWindow
          key={tab.id}
          tab={tab}
          onClose={() => closeTab(tab.id)}
          onFocus={() => focusTab(tab.id)}
          onUpdate={(patch) => updateTab(tab.id, patch)}
          onToggleMaximize={() => toggleMaximize(tab.id)}
        />
      ))}
    </FloatingTabsContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useFloatingTabs(): FloatingTabsState {
  const ctx = useContext(FloatingTabsContext)
  if (!ctx) throw new Error('useFloatingTabs must be used within FloatingTabsProvider')
  return ctx
}

type ResizeDir = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'
const RESIZE_HANDLES: { dir: ResizeDir; className: string }[] = [
  { dir: 'n', className: 'rz-n' }, { dir: 's', className: 'rz-s' },
  { dir: 'e', className: 'rz-e' }, { dir: 'w', className: 'rz-w' },
  { dir: 'ne', className: 'rz-ne' }, { dir: 'nw', className: 'rz-nw' },
  { dir: 'se', className: 'rz-se' }, { dir: 'sw', className: 'rz-sw' },
]

function FloatingWindow({ tab, onClose, onFocus, onUpdate, onToggleMaximize }: {
  tab: TabState
  onClose: () => void
  onFocus: () => void
  onUpdate: (patch: Partial<TabState>) => void
  onToggleMaximize: () => void
}) {
  const dragRef = useRef<{ startX: number; startY: number; origX: number; origY: number } | null>(null)
  const resizeRef = useRef<{ dir: ResizeDir; startX: number; startY: number; origX: number; origY: number; origW: number; origH: number } | null>(null)
  const [interacting, setInteracting] = useState(false)

  const onDragStart = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (tab.maximized) return
    onFocus()
    dragRef.current = { startX: e.clientX, startY: e.clientY, origX: tab.x, origY: tab.y }
    setInteracting(true)
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onDragMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragRef.current) return
    const dx = e.clientX - dragRef.current.startX
    const dy = e.clientY - dragRef.current.startY
    onUpdate({
      x: clamp(dragRef.current.origX + dx, 0, Math.max(0, window.innerWidth - 80)),
      y: clamp(dragRef.current.origY + dy, 0, Math.max(0, window.innerHeight - 40)),
    })
  }
  const onDragEnd = () => { dragRef.current = null; setInteracting(false) }

  const onResizeStart = (dir: ResizeDir) => (e: ReactPointerEvent<HTMLDivElement>) => {
    e.stopPropagation()
    onFocus()
    resizeRef.current = { dir, startX: e.clientX, startY: e.clientY, origX: tab.x, origY: tab.y, origW: tab.width, origH: tab.height }
    setInteracting(true)
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const onResizeMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const r = resizeRef.current
    if (!r) return
    const dx = e.clientX - r.startX
    const dy = e.clientY - r.startY
    const vw = window.innerWidth
    const vh = window.innerHeight
    const patch: Partial<TabState> = {}
    if (r.dir.includes('e')) {
      patch.width = clamp(r.origW + dx, MIN_WIDTH, vw - r.origX - 4)
    }
    if (r.dir.includes('w')) {
      const newW = clamp(r.origW - dx, MIN_WIDTH, r.origX + r.origW - 4)
      patch.width = newW
      patch.x = r.origX + (r.origW - newW)
    }
    if (r.dir.includes('s')) {
      patch.height = clamp(r.origH + dy, MIN_HEIGHT, vh - r.origY - 4)
    }
    if (r.dir.includes('n')) {
      const newH = clamp(r.origH - dy, MIN_HEIGHT, r.origY + r.origH - 4)
      patch.height = newH
      patch.y = r.origY + (r.origH - newH)
    }
    onUpdate(patch)
  }
  const onResizeEnd = () => { resizeRef.current = null; setInteracting(false) }

  return (
    <div className={`floating-tab ${tab.maximized ? 'maximized' : ''}`} style={{ left: tab.x, top: tab.y, width: tab.width, height: tab.height, zIndex: 300 + tab.z }} onPointerDownCapture={onFocus}>
      <div
        className="floating-tab-bar"
        onPointerDown={onDragStart}
        onPointerMove={onDragMove}
        onPointerUp={onDragEnd}
        onPointerCancel={onDragEnd}
        onDoubleClick={onToggleMaximize}
      >
        <span className="floating-tab-title">◧ SwipeAuctions</span>
        {/* Stop the bar's own drag-start handler from ever seeing pointerdown events that start on
            these buttons — otherwise it captures the pointer and the button never gets its click. */}
        <div className="floating-tab-controls" onPointerDown={(e) => e.stopPropagation()}>
          <button type="button" className="floating-tab-max" onClick={onToggleMaximize} aria-label={tab.maximized ? 'Restore' : 'Maximize'} title={tab.maximized ? 'Restore' : 'Maximize'}>
            {tab.maximized ? '❐' : '▢'}
          </button>
          <button type="button" className="floating-tab-close" onClick={onClose} aria-label="Close tab">✕</button>
        </div>
      </div>
      <div className="floating-tab-body">
        <iframe src={tab.path} title="SwipeAuctions" style={{ pointerEvents: interacting ? 'none' : 'auto' }} />
      </div>
      {!tab.maximized && RESIZE_HANDLES.map((h) => (
        <div
          key={h.dir}
          className={`floating-tab-resize ${h.className}`}
          onPointerDown={onResizeStart(h.dir)}
          onPointerMove={onResizeMove}
          onPointerUp={onResizeEnd}
          onPointerCancel={onResizeEnd}
        />
      ))}
    </div>
  )
}

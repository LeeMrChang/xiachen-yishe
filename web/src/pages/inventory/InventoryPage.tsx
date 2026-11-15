import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import type { InventoryMatrix, PageResult, ProductRow } from '@/api/types'

interface CellState {
  quantity: string
  onSale: boolean
}

function key(skuId: number, storeId: number): string {
  return `${skuId}:${storeId}`
}

export default function InventoryPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const rawId = searchParams.get('productId')
  const productId = rawId && !Number.isNaN(Number(rawId)) ? Number(rawId) : null

  const [matrix, setMatrix] = useState<InventoryMatrix | null>(null)
  const [cells, setCells] = useState<Record<string, CellState>>({})
  const [options, setOptions] = useState<ProductRow[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (productId) return
    api<PageResult<ProductRow>>('/api/admin/products?page=1&pageSize=100')
      .then((page) => setOptions(page.list))
      .catch((e) => setError(e instanceof ApiError ? e.message : '加载商品列表失败'))
  }, [productId])

  const load = useCallback(async (id: number) => {
    setLoading(true)
    setError('')
    try {
      const data = await api<InventoryMatrix>(`/api/admin/inventory?productId=${id}`)
      setMatrix(data)
      const next: Record<string, CellState> = {}
      for (const cell of data.cells) {
        next[key(cell.skuId, cell.storeId)] = { quantity: String(cell.quantity), onSale: cell.onSale }
      }
      setCells(next)
    } catch (e) {
      setMatrix(null)
      setError(e instanceof ApiError ? e.message : '加载库存失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (productId) {
      void load(productId)
    } else {
      setMatrix(null)
    }
  }, [productId, load])

  function setCell(skuId: number, storeId: number, patch: Partial<CellState>) {
    const cellKey = key(skuId, storeId)
    setCells((prev) => ({
      ...prev,
      [cellKey]: { ...(prev[cellKey] ?? { quantity: '0', onSale: true }), ...patch },
    }))
  }

  async function save() {
    if (!matrix) return
    setSaving(true)
    setError('')
    try {
      const items = matrix.cells.map((cell) => {
        const state = cells[key(cell.skuId, cell.storeId)]
        const quantity = Number(state?.quantity ?? 0)
        return {
          skuId: cell.skuId,
          storeId: cell.storeId,
          quantity: Number.isFinite(quantity) ? Math.max(0, Math.trunc(quantity)) : 0,
          onSale: state?.onSale ?? true,
        }
      })
      await api<number>('/api/admin/inventory', { method: 'PUT', body: JSON.stringify({ items }) })
      setNotice('库存已保存')
      window.setTimeout(() => setNotice(''), 2400)
      await load(matrix.productId)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存库存失败')
    } finally {
      setSaving(false)
    }
  }

  if (!productId) {
    return (
      <section>
        <header className="page-head">
          <div>
            <h1 className="page-title">库存维护</h1>
            <p className="page-desc">按商品维度维护三店库存，保存后 C 端实时生效</p>
          </div>
        </header>
        {error && <p className="form-error">{error}</p>}
        <div className="card card--pad">
          <label className="field">
            <span>选择要维护的商品</span>
            <select
              value=""
              onChange={(e) => {
                if (e.target.value) navigate(`/inventory?productId=${e.target.value}`)
              }}
            >
              <option value="">请选择商品…</option>
              {options.map((row) => (
                <option key={row.id} value={row.id}>
                  #{row.id} {row.title}
                </option>
              ))}
            </select>
          </label>
          <p className="section-hint">
            也可以从 <Link to="/products">商品维护</Link> 列表点「库存」直达。
          </p>
        </div>
      </section>
    )
  }

  return (
    <section>
      <header className="page-head">
        <div>
          <p className="crumb">
            <Link to="/products">商品维护</Link> / <Link to={`/products/${productId}`}>#{productId}</Link> / 库存
          </p>
          <h1 className="page-title">{matrix ? matrix.title : '库存维护'}</h1>
          <p className="page-desc">按 SKU 填写三店可售数量；「在售」取消勾选后该店该规格不出现在小程序</p>
        </div>
        <div className="page-head__actions">
          <Link className="button" to={`/products/${productId}`}>
            返回商品
          </Link>
          <button type="button" className="button button--primary" onClick={() => void save()} disabled={!matrix || saving}>
            {saving ? '保存中…' : '保存库存'}
          </button>
        </div>
      </header>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}
      {loading && <p className="empty">加载中…</p>}

      {matrix && (
        <div className="card">
          <table className="table inventory-table">
            <thead>
              <tr>
                <th>SKU</th>
                {matrix.stores.map((store) => (
                  <th key={store.id}>{store.name.replace('夏晨衣舍 · ', '')}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {matrix.skus.map((sku) => (
                <tr key={sku.id}>
                  <td>
                    <div className="sku-cell">
                      <strong>
                        {sku.color} / {sku.size}
                      </strong>
                      {sku.status !== 'ON' && <span className="chip chip--draft">已停用</span>}
                    </div>
                  </td>
                  {matrix.stores.map((store) => {
                    const state = cells[key(sku.id, store.id)] ?? { quantity: '0', onSale: true }
                    const quantity = Number(state.quantity)
                    return (
                      <td key={store.id}>
                        <div className="stock-cell">
                          <input
                            className={Number.isFinite(quantity) && quantity <= 2 ? 'input--warn' : ''}
                            inputMode="numeric"
                            value={state.quantity}
                            onChange={(e) => setCell(sku.id, store.id, { quantity: e.target.value })}
                          />
                          <label className="stock-cell__on">
                            <input
                              type="checkbox"
                              checked={state.onSale}
                              onChange={(e) => setCell(sku.id, store.id, { onSale: e.target.checked })}
                            />
                            在售
                          </label>
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          {matrix.skus.length === 0 && <p className="empty">该商品还没有 SKU，请先到商品编辑页维护</p>}
        </div>
      )}
    </section>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import { STATUS_LABEL, dateTime, yuan, type PageResult, type ProductRow } from '@/api/types'

const PAGE_SIZE = 20

export default function ProductListPage() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<ProductRow> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    if (status) params.set('status', status)
    try {
      setData(await api<PageResult<ProductRow>>(`/api/admin/products?${params.toString()}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, status, page])

  useEffect(() => {
    void load()
  }, [load])

  async function toggleStatus(row: ProductRow) {
    const action = row.status === 'ON' ? 'offline' : 'publish'
    const verb = action === 'offline' ? '下线' : '发布'
    if (!window.confirm(`确认${verb}「${row.title}」？`)) return
    setBusyId(row.id)
    setError('')
    try {
      await api(`/api/admin/products/${row.id}/${action}`, { method: 'POST' })
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : `${verb}失败`)
    } finally {
      setBusyId(null)
    }
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1

  return (
    <section>
      <header className="page-head">
        <div>
          <h1 className="page-title">商品维护</h1>
          <p className="page-desc">录图、定价、上架与门店库存，一套流程走完</p>
        </div>
        <button type="button" className="button button--primary" onClick={() => navigate('/products/new')}>
          新建商品
        </button>
      </header>

      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="搜索商品标题 / 副标题"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(1)
          }}
        />
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(1)
          }}
        >
          <option value="">全部状态</option>
          <option value="ON">在售</option>
          <option value="OFF">已下线</option>
          <option value="DRAFT">草稿</option>
        </select>
        <span className="toolbar__count">{data ? `共 ${data.total} 款` : ''}</span>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>商品</th>
              <th>分类</th>
              <th>售价</th>
              <th>总库存</th>
              <th>SKU</th>
              <th>状态</th>
              <th>更新时间</th>
              <th className="table__actions">操作</th>
            </tr>
          </thead>
          <tbody>
            {data?.list.map((row) => (
              <tr key={row.id}>
                <td>
                  <div className="product-cell">
                    {row.coverUrl ? (
                      <img className="thumb" src={row.coverUrl} alt="" />
                    ) : (
                      <span className="thumb thumb--empty">无图</span>
                    )}
                    <div>
                      <Link className="product-cell__title" to={`/products/${row.id}`}>
                        {row.title}
                      </Link>
                      <div className="product-cell__meta">#{row.id}</div>
                    </div>
                  </div>
                </td>
                <td>{row.category || '—'}</td>
                <td>{row.priceCent > 0 ? yuan(row.priceCent) : '—'}</td>
                <td className={row.totalQuantity <= 2 ? 'text-warn' : ''}>{row.totalQuantity}</td>
                <td>{row.skuCount}</td>
                <td>
                  <span className={`chip chip--${row.status.toLowerCase()}`}>
                    {STATUS_LABEL[row.status] ?? row.status}
                  </span>
                </td>
                <td className="text-muted">{dateTime(row.updatedAt)}</td>
                <td className="table__actions">
                  <Link className="link-button" to={`/products/${row.id}`}>
                    编辑
                  </Link>
                  <Link className="link-button" to={`/inventory?productId=${row.id}`}>
                    库存
                  </Link>
                  {row.status !== 'DRAFT' && (
                    <button
                      type="button"
                      className="link-button"
                      disabled={busyId === row.id}
                      onClick={() => void toggleStatus(row)}
                    >
                      {row.status === 'ON' ? '下线' : '发布'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && data?.list.length === 0 && <p className="empty">还没有符合条件的商品</p>}
        {loading && <p className="empty">加载中…</p>}
      </div>

      <div className="pager">
        <button type="button" className="button" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
          上一页
        </button>
        <span>
          {page} / {totalPages}
        </span>
        <button type="button" className="button" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
          下一页
        </button>
      </div>
    </section>
  )
}

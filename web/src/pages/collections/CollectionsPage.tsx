import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import { STATUS_LABEL, dateTime, type CollectionRow } from '@/api/types'

export default function CollectionsPage() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [rows, setRows] = useState<CollectionRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const params = new URLSearchParams()
    if (keyword.trim()) params.set('keyword', keyword.trim())
    if (status) params.set('status', status)
    const qs = params.toString()
    try {
      setRows(await api<CollectionRow[]>(`/api/admin/collections${qs ? `?${qs}` : ''}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载系列失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, status])

  useEffect(() => {
    void load()
  }, [load])

  async function remove(row: CollectionRow) {
    if (!window.confirm(`确认删除系列「${row.name}」？该操作不可恢复。`)) return
    setBusyId(row.id)
    setError('')
    try {
      await api(`/api/admin/collections/${row.id}`, { method: 'DELETE' })
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '删除失败')
    } finally {
      setBusyId(null)
    }
  }

  async function toggleStatus(row: CollectionRow) {
    const action = row.status === 'ON' ? 'offline' : 'publish'
    const verb = action === 'offline' ? '下线' : '上线'
    if (!window.confirm(`确认${verb}系列「${row.name}」？`)) return
    setBusyId(row.id)
    setError('')
    try {
      await api(`/api/admin/collections/${row.id}/${action}`, { method: 'POST' })
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : `${verb}失败`)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section>
      <header className="page-head">
        <div>
          <h1 className="page-title">系列维护</h1>
          <p className="page-desc">系列是首页「系列」模块的数据源；先建系列、加商品，再上线</p>
        </div>
        <button type="button" className="button button--primary" onClick={() => navigate('/collections/new')}>
          新建系列
        </button>
      </header>

      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="搜索系列名称 / 故事"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">全部状态</option>
          <option value="ON">已上线</option>
          <option value="OFF">已下线</option>
          <option value="DRAFT">草稿</option>
        </select>
        <span className="toolbar__count">{`共 ${rows.length} 个系列`}</span>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>系列</th>
              <th>商品</th>
              <th>排序</th>
              <th>状态</th>
              <th>更新时间</th>
              <th className="table__actions">操作</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>
                  <div className="product-cell">
                    {row.coverUrl ? (
                      <img className="thumb" src={row.coverUrl} alt="" />
                    ) : (
                      <span className="thumb thumb--empty">无图</span>
                    )}
                    <div>
                      <Link className="product-cell__title" to={`/collections/${row.id}`}>
                        {row.name}
                      </Link>
                      <div className="product-cell__meta">{row.story || '暂无系列故事'}</div>
                    </div>
                  </div>
                </td>
                <td>{row.productCount} 件</td>
                <td>{row.sortNo}</td>
                <td>
                  <span className={`chip chip--${row.status.toLowerCase()}`}>
                    {STATUS_LABEL[row.status] || row.status}
                  </span>
                </td>
                <td>{dateTime(row.updatedAt)}</td>
                <td className="table__actions">
                  <Link className="link-button" to={`/collections/${row.id}`}>
                    编辑
                  </Link>
                  <button
                    type="button"
                    className="link-button"
                    disabled={busyId === row.id}
                    onClick={() => void toggleStatus(row)}
                  >
                    {row.status === 'ON' ? '下线' : '上线'}
                  </button>
                  <button
                    type="button"
                    className="link-button link-button--danger"
                    disabled={busyId === row.id}
                    onClick={() => void remove(row)}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && rows.length === 0 && <p className="empty">还没有系列，点右上角新建一个</p>}
      </div>
    </section>
  )
}

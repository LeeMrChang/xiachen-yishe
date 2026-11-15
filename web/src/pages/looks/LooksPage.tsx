import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import { STATUS_LABEL, dateTime, type LookRow, type PageResult } from '@/api/types'

const PAGE_SIZE = 20

export default function LooksPage() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<LookRow> | null>(null)
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
      setData(await api<PageResult<LookRow>>(`/api/admin/looks?${params.toString()}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载造型失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, status, page])

  useEffect(() => {
    void load()
  }, [load])

  async function toggleStatus(row: LookRow) {
    const action = row.status === 'ON' ? 'offline' : 'publish'
    const verb = action === 'offline' ? '下线' : '发布'
    if (!window.confirm(`确认${verb}造型「${row.title}」？`)) return
    setBusyId(row.id)
    setError('')
    try {
      await api(`/api/admin/looks/${row.id}/${action}`, { method: 'POST' })
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
          <h1 className="page-title">造型维护</h1>
          <p className="page-desc">一套搭配 3 件左右，封面与单品都维护好再发布</p>
        </div>
        <button type="button" className="button button--primary" onClick={() => navigate('/looks/new')}>
          新建造型
        </button>
      </header>

      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="搜索造型标题 / 描述"
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
          <option value="ON">已发布</option>
          <option value="OFF">已下线</option>
          <option value="DRAFT">草稿</option>
        </select>
        <span className="toolbar__count">{data ? `共 ${data.total} 套` : ''}</span>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>造型</th>
              <th>单品</th>
              <th>排序</th>
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
                      <Link className="product-cell__title" to={`/looks/${row.id}`}>
                        {row.title}
                      </Link>
                      <div className="product-cell__meta">{row.description || '暂无描述'}</div>
                    </div>
                  </div>
                </td>
                <td>{row.itemCount} 件</td>
                <td>{row.sortNo}</td>
                <td>
                  <span className={`chip chip--${row.status.toLowerCase()}`}>{STATUS_LABEL[row.status] || row.status}</span>
                </td>
                <td>{dateTime(row.updatedAt)}</td>
                <td className="table__actions">
                  <Link className="link-button" to={`/looks/${row.id}`}>
                    编辑
                  </Link>
                  <button
                    type="button"
                    className="link-button"
                    disabled={busyId === row.id}
                    onClick={() => void toggleStatus(row)}
                  >
                    {row.status === 'ON' ? '下线' : '发布'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && data && data.list.length === 0 && <p className="empty">还没有造型</p>}
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

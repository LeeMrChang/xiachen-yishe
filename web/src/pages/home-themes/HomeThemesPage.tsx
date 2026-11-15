import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import { dateTime, themeStatusLabel, type HomeThemeRow } from '@/api/types'

export default function HomeThemesPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState<HomeThemeRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setRows(await api<HomeThemeRow[]>('/api/admin/home-themes'))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载主题失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function act(row: HomeThemeRow, action: 'activate' | 'offline') {
    const verb = action === 'activate' ? '设为当前主题' : '下线'
    if (!window.confirm(`确认${verb}「${row.name}」？`)) return
    setBusyId(row.id)
    setError('')
    try {
      await api(`/api/admin/home-themes/${row.id}/${action}`, { method: 'POST' })
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
          <h1 className="page-title">首页主题</h1>
          <p className="page-desc">一套主题决定首页首屏与模块顺序；到时间窗自动切换，也可手动设为当前</p>
        </div>
        <button type="button" className="button button--primary" onClick={() => navigate('/home-themes/new')}>
          新建主题
        </button>
      </header>

      {error && <p className="form-error">{error}</p>}

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>主题</th>
              <th>时间窗</th>
              <th>模块</th>
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
                      <Link className="product-cell__title" to={`/home-themes/${row.id}`}>
                        {row.name}
                      </Link>
                      <div className="product-cell__meta">{row.copyText || '暂无文案'}</div>
                    </div>
                  </div>
                </td>
                <td className="text-muted">
                  {row.startAt || row.endAt
                    ? `${row.startAt ? dateTime(row.startAt) : '不限'} ~ ${row.endAt ? dateTime(row.endAt) : '不限'}`
                    : '长期有效'}
                </td>
                <td>{row.moduleCount} 个</td>
                <td>
                  <span className={`chip chip--${row.current ? 'on' : row.status.toLowerCase()}`}>
                    {themeStatusLabel(row)}
                  </span>
                </td>
                <td className="text-muted">{dateTime(row.updatedAt)}</td>
                <td className="table__actions">
                  <Link className="link-button" to={`/home-themes/${row.id}`}>
                    编辑
                  </Link>
                  {row.status === 'ON' && !row.current && (
                    <button
                      type="button"
                      className="link-button"
                      disabled={busyId === row.id}
                      onClick={() => void act(row, 'activate')}
                    >
                      设为当前
                    </button>
                  )}
                  {row.status !== 'OFF' && (
                    <button
                      type="button"
                      className="link-button link-button--danger"
                      disabled={busyId === row.id}
                      onClick={() => void act(row, 'offline')}
                    >
                      下线
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && rows.length === 0 && <p className="empty">还没有主题</p>}
        {loading && <p className="empty">加载中…</p>}
      </div>
    </section>
  )
}

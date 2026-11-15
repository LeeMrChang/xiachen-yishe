import { useCallback, useEffect, useState } from 'react'
import { ApiError, api, getStaff } from '@/api/client'
import {
  APPOINTMENT_STATUS_LABEL,
  clock,
  dateTime,
  todayKey,
  yuan,
  type AppointmentDetail,
  type AppointmentRow,
  type PageResult,
  type StoreOption,
} from '@/api/types'

const PAGE_SIZE = 50

export default function AppointmentsPage() {
  const staff = getStaff()
  const isAdmin = staff?.role === 'ADMIN'
  const [date, setDate] = useState(todayKey())
  const [status, setStatus] = useState('')
  const [storeId, setStoreId] = useState('')
  const [page, setPage] = useState(1)
  const [stores, setStores] = useState<StoreOption[]>([])
  const [data, setData] = useState<PageResult<AppointmentRow> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [detail, setDetail] = useState<AppointmentDetail | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ date, page: String(page), pageSize: String(PAGE_SIZE) })
    if (status) params.set('status', status)
    if (isAdmin && storeId) params.set('storeId', storeId)
    try {
      setData(await api<PageResult<AppointmentRow>>(`/api/admin/appointments?${params.toString()}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载预约失败')
    } finally {
      setLoading(false)
    }
  }, [date, status, storeId, page])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!isAdmin) return
    api<StoreOption[]>('/api/admin/stores')
      .then(setStores)
      .catch(() => setStores([]))
  }, [])

  async function openDetail(id: number) {
    setError('')
    try {
      setDetail(await api<AppointmentDetail>(`/api/admin/appointments/${id}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载预约明细失败')
    }
  }

  async function act(row: AppointmentRow, target: string) {
    const verb = target === 'CONFIRMED' ? '确认' : target === 'DONE' ? '标记到店' : '取消'
    if (!window.confirm(`确认${verb}「${row.contactName || row.phone}」的预约？`)) return
    setBusyId(row.id)
    setError('')
    setNotice('')
    try {
      await api(`/api/admin/appointments/${row.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: target }),
      })
      setNotice(`已${verb}`)
      await load()
      if (detail?.id === row.id) {
        await openDetail(row.id)
      }
      window.setTimeout(() => setNotice(''), 2400)
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
          <h1 className="page-title">预约看板</h1>
          <p className="page-desc">
            {date} · 共 {data?.total ?? 0} 条{isAdmin ? '' : ` · ${staff?.displayName || ''}本店`}
          </p>
        </div>
        <div className="page-head__actions">
          <button type="button" className="button" onClick={() => void load()} disabled={loading}>
            刷新
          </button>
        </div>
      </header>

      <div className="toolbar">
        <input
          type="date"
          style={{ width: 170 }}
          value={date}
          onChange={(e) => {
            setDate(e.target.value)
            setPage(1)
          }}
        />
        {isAdmin && (
          <select
            value={storeId}
            onChange={(e) => {
              setStoreId(e.target.value)
              setPage(1)
            }}
          >
            <option value="">全部门店</option>
            {stores.map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </select>
        )}
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(1)
          }}
        >
          <option value="">全部状态</option>
          {Object.entries(APPOINTMENT_STATUS_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <span className="toolbar__count">{loading ? '加载中…' : ''}</span>
      </div>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>时段</th>
              <th>门店</th>
              <th>联系人</th>
              <th>手机号</th>
              <th>件数</th>
              <th>状态</th>
              <th>提交时间</th>
              <th className="table__actions">操作</th>
            </tr>
          </thead>
          <tbody>
            {data?.list.map((row) => (
              <tr key={row.id}>
                <td>{clock(row.visitTime)}</td>
                <td>{row.storeName}</td>
                <td>
                  {row.contactName || <span className="text-muted">未填写</span>}
                  {row.remark && <div className="product-cell__meta">备注：{row.remark}</div>}
                </td>
                <td>{row.phone}</td>
                <td>{row.itemCount} 件</td>
                <td>
                  <span className={`chip chip--${row.status.toLowerCase()}`}>{row.statusText}</span>
                </td>
                <td>{dateTime(row.createdAt)}</td>
                <td className="table__actions">
                  <button type="button" className="link-button" onClick={() => void openDetail(row.id)}>
                    查看
                  </button>
                  {row.status === 'PENDING' && (
                    <button
                      type="button"
                      className="link-button"
                      disabled={busyId === row.id}
                      onClick={() => void act(row, 'CONFIRMED')}
                    >
                      确认
                    </button>
                  )}
                  {row.status === 'CONFIRMED' && (
                    <button
                      type="button"
                      className="link-button"
                      disabled={busyId === row.id}
                      onClick={() => void act(row, 'DONE')}
                    >
                      到店
                    </button>
                  )}
                  {(row.status === 'PENDING' || row.status === 'CONFIRMED') && (
                    <button
                      type="button"
                      className="link-button link-button--danger"
                      disabled={busyId === row.id}
                      onClick={() => void act(row, 'CANCELED')}
                    >
                      取消
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && data && data.list.length === 0 && <p className="empty">这一天还没有预约</p>}
      </div>

      {detail && (
        <div className="card card--pad appointment-detail">
          <div className="section-head">
            <div>
              <h2 className="section-title">
                {detail.storeName} · {clock(detail.visitTime)}
              </h2>
              <p className="section-hint">
                {detail.contactName || '未填写称呼'} · {detail.phone}
                {detail.storeAddress ? ` · ${detail.storeAddress}` : ''}
              </p>
            </div>
            <span className={`chip chip--${detail.status.toLowerCase()}`}>{detail.statusText}</span>
          </div>
          {detail.remark && <p className="text-muted">备注：{detail.remark}</p>}
          <div className="detail-items">
            {detail.items.map((item) => (
              <figure className="detail-item" key={item.skuId}>
                {item.coverUrl ? <img src={item.coverUrl} alt="" /> : <span className="thumb thumb--empty">无图</span>}
                <figcaption>
                  <strong>{item.title}</strong>
                  <span>
                    {item.color} · {item.size}
                  </span>
                  <span>{yuan(item.priceCent)}</span>
                </figcaption>
              </figure>
            ))}
            {detail.items.length === 0 && <p className="empty">该预约没有商品明细</p>}
          </div>
          <div className="form-foot">
            <button type="button" className="button" onClick={() => setDetail(null)}>
              收起明细
            </button>
            {detail.status === 'PENDING' && (
              <button type="button" className="button button--primary" onClick={() => void act(detail, 'CONFIRMED')}>
                确认预约
              </button>
            )}
            {detail.status === 'CONFIRMED' && (
              <button type="button" className="button button--primary" onClick={() => void act(detail, 'DONE')}>
                标记已到店
              </button>
            )}
          </div>
        </div>
      )}

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

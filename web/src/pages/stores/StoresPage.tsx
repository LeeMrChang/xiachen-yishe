import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, api, getStaff } from '@/api/client'
import type { StoreCover, UploadResult } from '@/api/types'

interface StoreDraft {
  code: string
  name: string
  address: string
  phone: string
  openTime: string
  closeTime: string
  coverUrl: string
}

const EMPTY_DRAFT: StoreDraft = {
  code: '',
  name: '',
  address: '',
  phone: '',
  openTime: '',
  closeTime: '',
  coverUrl: '',
}

function toInput(value: string | null) {
  return value ? value.slice(0, 5) : ''
}

function toApiTime(value: string) {
  return value ? `${value}:00` : null
}

export default function StoresPage() {
  const staff = getStaff()
  const isAdmin = staff?.role === 'ADMIN'

  const fileRef = useRef<HTMLInputElement>(null)
  const targetRef = useRef<number | 'new' | null>(null)

  const [rows, setRows] = useState<StoreCover[]>([])
  const [loading, setLoading] = useState(false)
  const [uploadingId, setUploadingId] = useState<number | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<StoreDraft>(EMPTY_DRAFT)
  const [creatingSaving, setCreatingSaving] = useState(false)
  const [creatingUploading, setCreatingUploading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setRows(await api<StoreCover[]>('/api/admin/stores/covers'))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载门店失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const visibleRows = isAdmin ? rows : rows.filter((row) => row.id === staff?.storeId)

  function patch(id: number, changes: Partial<StoreCover>) {
    setRows((prev) => prev.map((row) => (row.id === id ? { ...row, ...changes } : row)))
  }

  function flash(message: string) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 3000)
  }

  function pick(id: number | 'new') {
    targetRef.current = id
    fileRef.current?.click()
  }

  function checkHours(openTime: string, closeTime: string, label: string) {
    if ((openTime && !closeTime) || (!openTime && closeTime)) {
      setError(`「${label}」的营业时间需同时填写开始和结束`)
      return false
    }
    if (openTime && closeTime && openTime >= closeTime) {
      setError(`「${label}」的营业结束时间必须晚于开始时间`)
      return false
    }
    return true
  }

  async function onFile(file: File) {
    const target = targetRef.current
    if (target == null) return
    setError('')
    try {
      const payload = new FormData()
      payload.append('file', file)
      if (target === 'new') {
        setCreatingUploading(true)
        const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
        setDraft((prev) => ({ ...prev, coverUrl: uploaded.url }))
      } else {
        setUploadingId(target)
        const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
        const saved = await api<StoreCover>(`/api/admin/stores/${target}/cover`, {
          method: 'PUT',
          body: JSON.stringify({ coverUrl: uploaded.url }),
        })
        patch(target, saved)
        flash('封面已更新，小程序下拉刷新即可看到')
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '封面更新失败')
    } finally {
      setUploadingId(null)
      setCreatingUploading(false)
      targetRef.current = null
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function save(store: StoreCover) {
    setError('')
    if (!store.name.trim()) {
      setError('请填写门店名')
      return
    }
    const openTime = toInput(store.openTime)
    const closeTime = toInput(store.closeTime)
    if (!checkHours(openTime, closeTime, store.name)) return
    setSavingId(store.id)
    try {
      const saved = await api<StoreCover>(`/api/admin/stores/${store.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: store.name,
          address: store.address,
          phone: store.phone,
          openTime: toApiTime(openTime),
          closeTime: toApiTime(closeTime),
        }),
      })
      patch(store.id, saved)
      flash(`「${saved.name}」已保存，小程序下拉刷新即可看到`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存失败')
    } finally {
      setSavingId(null)
    }
  }

  async function create() {
    setError('')
    if (!draft.code.trim() || !draft.name.trim()) {
      setError('请填写门店编码和门店名')
      return
    }
    if (!checkHours(draft.openTime, draft.closeTime, draft.name)) return
    setCreatingSaving(true)
    try {
      const saved = await api<StoreCover>('/api/admin/stores', {
        method: 'POST',
        body: JSON.stringify({
          code: draft.code,
          name: draft.name,
          address: draft.address,
          phone: draft.phone,
          openTime: toApiTime(draft.openTime),
          closeTime: toApiTime(draft.closeTime),
          coverUrl: draft.coverUrl,
        }),
      })
      setDraft(EMPTY_DRAFT)
      setCreating(false)
      await load()
      flash(`「${saved.name}」已创建并上线`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '创建门店失败')
    } finally {
      setCreatingSaving(false)
    }
  }

  async function toggleStatus(store: StoreCover) {
    const next = store.status === 'ON' ? 'OFF' : 'ON'
    const verb = next === 'OFF' ? '下线' : '上线'
    if (!window.confirm(`确认${verb}「${store.name}」？${next === 'OFF' ? '下线后顾客端不再展示该门店。' : ''}`)) {
      return
    }
    setError('')
    setSavingId(store.id)
    try {
      const saved = await api<StoreCover>(`/api/admin/stores/${store.id}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status: next }),
      })
      patch(store.id, saved)
      flash(`「${saved.name}」已${verb}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : `${verb}失败`)
    } finally {
      setSavingId(null)
    }
  }

  return (
    <section>
      <header className="page-head">
        <div>
          <h1 className="page-title">门店</h1>
          <p className="page-desc">门店资料会同步到小程序；封面用于首页门店卡，建议横图 16:10</p>
        </div>
        {isAdmin && (
          <div className="page-head__actions">
            <button
              type="button"
              className="button button--primary"
              onClick={() => {
                setCreating((prev) => !prev)
                setDraft(EMPTY_DRAFT)
                setError('')
              }}
            >
              {creating ? '收起新增' : '新增门店'}
            </button>
          </div>
        )}
      </header>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}

      {creating && (
        <div className="card card--pad">
          <div className="section-head">
            <div>
              <h2 className="section-title">新增门店</h2>
              <p className="section-hint">编码创建后不可修改；新门店默认上线，排序排在最后</p>
            </div>
          </div>
          <div className="store-card__form">
            <label className="field">
              <span>编码</span>
              <input
                value={draft.code}
                maxLength={32}
                placeholder="如 NANSHAN"
                onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })}
              />
            </label>
            <label className="field">
              <span>门店名</span>
              <input
                value={draft.name}
                maxLength={64}
                placeholder="如 夏晨衣舍 · 南山店"
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </label>
            <label className="field">
              <span>地址</span>
              <input
                value={draft.address}
                maxLength={255}
                onChange={(e) => setDraft({ ...draft, address: e.target.value })}
              />
            </label>
            <label className="field">
              <span>电话</span>
              <input
                value={draft.phone}
                maxLength={32}
                onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
              />
            </label>
            <label className="field">
              <span>营业开始</span>
              <input
                type="time"
                value={draft.openTime}
                onChange={(e) => setDraft({ ...draft, openTime: e.target.value })}
              />
            </label>
            <label className="field">
              <span>营业结束</span>
              <input
                type="time"
                value={draft.closeTime}
                onChange={(e) => setDraft({ ...draft, closeTime: e.target.value })}
              />
            </label>
          </div>
          <div className="store-card__actions">
            <div className="store-card__cover-new">
              <div
                className="store-card__cover"
                style={draft.coverUrl ? { backgroundImage: `url(${draft.coverUrl})` } : undefined}
              >
                {!draft.coverUrl && <span>暂无封面</span>}
              </div>
              <button
                type="button"
                className="link-button"
                disabled={creatingUploading}
                onClick={() => pick('new')}
              >
                {creatingUploading ? '上传中…' : draft.coverUrl ? '更换封面' : '上传封面（可选）'}
              </button>
            </div>
            <button
              type="button"
              className="button button--primary"
              disabled={creatingSaving}
              onClick={() => void create()}
            >
              {creatingSaving ? '创建中…' : '创建门店'}
            </button>
          </div>
        </div>
      )}

      {loading && <p className="empty">加载中…</p>}

      {!loading && (
        <div className="card card--pad">
          {visibleRows.map((store) => (
            <div className={`store-card ${store.status === 'ON' ? '' : 'is-off'}`} key={store.id}>
              <div className="store-card__head">
                <div
                  className="store-card__cover"
                  style={store.coverUrl ? { backgroundImage: `url(${store.coverUrl})` } : undefined}
                >
                  {!store.coverUrl && <span>暂无封面</span>}
                </div>
                <div className="store-card__meta">
                  <strong>{store.name}</strong>
                  <span>
                    编码 {store.code} · {store.status === 'ON' ? '营业中' : '已下线'}
                  </span>
                </div>
                <button
                  type="button"
                  className="button"
                  disabled={uploadingId === store.id}
                  onClick={() => pick(store.id)}
                >
                  {uploadingId === store.id ? '上传中…' : store.coverUrl ? '更换封面' : '上传封面'}
                </button>
              </div>

              <div className="store-card__form">
                <label className="field">
                  <span>门店名</span>
                  <input
                    value={store.name}
                    maxLength={64}
                    onChange={(e) => patch(store.id, { name: e.target.value })}
                  />
                </label>
                <label className="field">
                  <span>地址</span>
                  <input
                    value={store.address}
                    maxLength={255}
                    onChange={(e) => patch(store.id, { address: e.target.value })}
                  />
                </label>
                <label className="field">
                  <span>电话</span>
                  <input
                    value={store.phone}
                    maxLength={32}
                    onChange={(e) => patch(store.id, { phone: e.target.value })}
                  />
                </label>
                <label className="field">
                  <span>营业开始</span>
                  <input
                    type="time"
                    value={toInput(store.openTime)}
                    onChange={(e) => patch(store.id, { openTime: e.target.value || null })}
                  />
                </label>
                <label className="field">
                  <span>营业结束</span>
                  <input
                    type="time"
                    value={toInput(store.closeTime)}
                    onChange={(e) => patch(store.id, { closeTime: e.target.value || null })}
                  />
                </label>
              </div>

              <div className="store-card__actions">
                <div className="store-card__hints">
                  <span className="section-hint">营业时间留空表示不限时段，预约会按填写的时间校验</span>
                  {isAdmin && (
                    <button
                      type="button"
                      className={`link-button ${store.status === 'ON' ? 'link-button--danger' : ''}`}
                      disabled={savingId === store.id}
                      onClick={() => void toggleStatus(store)}
                    >
                      {store.status === 'ON' ? '下线门店' : '重新上线'}
                    </button>
                  )}
                </div>
                <button
                  type="button"
                  className="button button--primary"
                  disabled={savingId === store.id}
                  onClick={() => void save(store)}
                >
                  {savingId === store.id ? '保存中…' : '保存门店信息'}
                </button>
              </div>
            </div>
          ))}
          {visibleRows.length === 0 && <p className="empty">还没有门店数据</p>}
        </div>
      )}

      <input
        ref={fileRef}
        type="file"
        accept="image/jpeg,image/png"
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) void onFile(file)
        }}
      />
    </section>
  )
}

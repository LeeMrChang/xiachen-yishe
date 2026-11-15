import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import {
  HOME_MODULE_LABEL,
  dateTime,
  themeStatusLabel,
  type CollectionOption,
  type HomeThemeDetail,
  type StoreCover,
  type UploadResult,
} from '@/api/types'

interface FormState {
  name: string
  copyText: string
  coverUrl: string
  startAt: string
  endAt: string
  status: string
}

interface ModuleDraft {
  key: string
  type: string
  refId: string
  imageUrl: string
  enabled: boolean
}

const EMPTY_FORM: FormState = {
  name: '',
  copyText: '',
  coverUrl: '',
  startAt: '',
  endAt: '',
  status: 'DRAFT',
}

const MODULE_TYPES = ['BANNER', 'RECOMMEND', 'COLLECTION', 'LOOK', 'STORE']
const SINGLE_TYPES = new Set(['BANNER', 'RECOMMEND', 'LOOK', 'STORE'])

function newKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random()}`
}

function toInput(value: string | null): string {
  return value ? value.slice(0, 16) : ''
}

function fromInput(value: string): string | null {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

export default function HomeThemeEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const themeId = id ? Number(id) : null
  const coverRef = useRef<HTMLInputElement>(null)
  const moduleImageRef = useRef<HTMLInputElement>(null)
  const moduleImageTarget = useRef<string | null>(null)

  const [detail, setDetail] = useState<HomeThemeDetail | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [modules, setModules] = useState<ModuleDraft[]>([])
  const [collections, setCollections] = useState<CollectionOption[]>([])
  const [stores, setStores] = useState<StoreCover[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [savingModules, setSavingModules] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const applyDetail = useCallback((theme: HomeThemeDetail) => {
    setDetail(theme)
    setForm({
      name: theme.name,
      copyText: theme.copyText ?? '',
      coverUrl: theme.coverUrl,
      startAt: toInput(theme.startAt),
      endAt: toInput(theme.endAt),
      status: theme.status,
    })
    setModules(
      theme.modules.map((module) => ({
        key: newKey(),
        type: module.type,
        refId: module.refId == null ? '' : String(module.refId),
        imageUrl: module.imageUrl,
        enabled: module.enabled,
      }))
    )
  }, [])

  const load = useCallback(async (targetId: number) => {
    setLoading(true)
    setError('')
    try {
      applyDetail(await api<HomeThemeDetail>(`/api/admin/home-themes/${targetId}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载主题失败')
    } finally {
      setLoading(false)
    }
  }, [applyDetail])

  useEffect(() => {
    if (themeId) {
      void load(themeId)
    } else {
      setDetail(null)
      setForm(EMPTY_FORM)
      setModules([])
    }
  }, [themeId, load])

  useEffect(() => {
    api<CollectionOption[]>('/api/admin/collections').then(setCollections).catch(() => setCollections([]))
    api<StoreCover[]>('/api/admin/stores/covers').then(setStores).catch(() => setStores([]))
  }, [])

  const flash = useCallback((message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }, [])

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function saveBasics(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!form.name.trim()) {
      setError('请填写主题名')
      return
    }
    const body = JSON.stringify({
      name: form.name.trim(),
      copyText: form.copyText,
      coverUrl: form.coverUrl,
      startAt: fromInput(form.startAt),
      endAt: fromInput(form.endAt),
      status: form.status,
    })
    setSaving(true)
    try {
      if (themeId) {
        applyDetail(await api<HomeThemeDetail>(`/api/admin/home-themes/${themeId}`, { method: 'PUT', body }))
        flash('主题信息已保存')
      } else {
        const created = await api<HomeThemeDetail>('/api/admin/home-themes', { method: 'POST', body })
        flash('已创建，接着编排模块')
        navigate(`/home-themes/${created.id}`, { replace: true })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function uploadCover(file: File) {
    if (!themeId) return
    setUploading(true)
    setError('')
    try {
      const payload = new FormData()
      payload.append('file', file)
      const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
      setForm((prev) => ({ ...prev, coverUrl: uploaded.url }))
      flash('封面已上传，记得保存主题信息')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '上传失败')
    } finally {
      setUploading(false)
      if (coverRef.current) coverRef.current.value = ''
    }
  }

  function pickModuleImage(key: string) {
    moduleImageTarget.current = key
    moduleImageRef.current?.click()
  }

  async function uploadModuleImage(file: File) {
    const key = moduleImageTarget.current
    if (!key) return
    setUploading(true)
    setError('')
    try {
      const payload = new FormData()
      payload.append('file', file)
      const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
      setModules((prev) => prev.map((item) => (item.key === key ? { ...item, imageUrl: uploaded.url } : item)))
      flash('模块配图已上传，记得保存模块顺序')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '上传失败')
    } finally {
      setUploading(false)
      moduleImageTarget.current = null
      if (moduleImageRef.current) moduleImageRef.current.value = ''
    }
  }

  function addModule(type: string) {
    if (SINGLE_TYPES.has(type) && modules.some((item) => item.type === type)) {
      setError(`「${HOME_MODULE_LABEL[type]}」模块只能有一个`)
      return
    }
    if (type === 'COLLECTION' && modules.filter((item) => item.type === type).length >= 3) {
      setError('系列模块最多 3 个')
      return
    }
    setError('')
    setModules((prev) => [
      ...prev,
      { key: newKey(), type, refId: '', imageUrl: '', enabled: true },
    ])
  }

  function updateModule(key: string, patch: Partial<ModuleDraft>) {
    setModules((prev) => prev.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  }

  function moveModule(index: number, delta: number) {
    setModules((prev) => {
      const next = [...prev]
      const target = index + delta
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  function removeModule(key: string) {
    setModules((prev) => prev.filter((item) => item.key !== key))
  }

  async function saveModules() {
    if (!themeId) return
    setError('')
    for (const item of modules) {
      if (item.type === 'COLLECTION' && !item.refId) {
        setError('系列模块需要选择具体系列')
        return
      }
    }
    setSavingModules(true)
    try {
      applyDetail(
        await api<HomeThemeDetail>(`/api/admin/home-themes/${themeId}/modules`, {
          method: 'PUT',
          body: JSON.stringify({
            modules: modules.map((item) => ({
              type: item.type,
              refId: item.refId ? Number(item.refId) : null,
              imageUrl: item.imageUrl,
              enabled: item.enabled,
            })),
          }),
        })
      )
      flash('模块已保存，小程序下拉刷新可见')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '模块保存失败')
    } finally {
      setSavingModules(false)
    }
  }

  async function changeStatus(action: 'activate' | 'offline') {
    if (!themeId) return
    const verb = action === 'activate' ? '设为当前主题' : '下线'
    if (!window.confirm(`确认${verb}？`)) return
    setError('')
    try {
      applyDetail(await api<HomeThemeDetail>(`/api/admin/home-themes/${themeId}/${action}`, { method: 'POST' }))
      flash(action === 'activate' ? '已设为当前主题' : '已下线')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : `${verb}失败`)
    }
  }

  return (
    <section>
      <header className="page-head">
        <div>
          <p className="crumb">
            <Link to="/home-themes">首页主题</Link> / {themeId ? `#${themeId}` : '新建'}
          </p>
          <h1 className="page-title">{themeId ? form.name || '主题编辑' : '新建主题'}</h1>
          <p className="page-desc">
            {detail ? (
              <>
                <span className={`chip chip--${detail.current ? 'on' : detail.status.toLowerCase()}`}>
                  {themeStatusLabel(detail)}
                </span>
                <span className="page-desc__sep">更新于 {dateTime(detail.updatedAt)}</span>
              </>
            ) : (
              '先保存主题信息，再编排首页模块'
            )}
          </p>
        </div>
        <div className="page-head__actions">
          {themeId && detail && detail.status === 'ON' && !detail.current && (
            <button type="button" className="button button--primary" onClick={() => void changeStatus('activate')}>
              设为当前
            </button>
          )}
          {themeId && detail && detail.status !== 'OFF' && (
            <button type="button" className="button" onClick={() => void changeStatus('offline')}>
              下线
            </button>
          )}
        </div>
      </header>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}
      {loading && <p className="empty">加载中…</p>}

      <form className="card card--pad" onSubmit={saveBasics}>
        <h2 className="section-title">主题信息</h2>
        <div className="hero-config">
          <div
            className="hero-preview"
            style={form.coverUrl ? { backgroundImage: `url(${form.coverUrl})` } : undefined}
          >
            <span className="hero-preview__brand">夏晨衣舍</span>
            <div className="hero-preview__copy">
              <strong>{form.name || '主题名'}</strong>
              <span>{form.copyText || '主题文案'}</span>
            </div>
          </div>
          <div className="hero-fields">
            <label className="field">
              <span>主题名（首屏大标题）*</span>
              <input value={form.name} maxLength={64} onChange={(e) => setField('name', e.target.value)} />
            </label>
            <label className="field">
              <span>主题文案（副标题）</span>
              <input value={form.copyText} maxLength={255} onChange={(e) => setField('copyText', e.target.value)} />
            </label>
            <div className="theme-window">
              <label className="field">
                <span>开始时间（可空）</span>
                <input type="datetime-local" value={form.startAt} onChange={(e) => setField('startAt', e.target.value)} />
              </label>
              <label className="field">
                <span>结束时间（可空）</span>
                <input type="datetime-local" value={form.endAt} onChange={(e) => setField('endAt', e.target.value)} />
              </label>
              <label className="field">
                <span>状态</span>
                <select value={form.status} onChange={(e) => setField('status', e.target.value)}>
                  <option value="DRAFT">草稿</option>
                  <option value="ON">启用</option>
                  <option value="OFF">已下线</option>
                </select>
              </label>
            </div>
            {themeId ? (
              <button type="button" className="button" disabled={uploading} onClick={() => coverRef.current?.click()}>
                {uploading ? '上传中…' : form.coverUrl ? '更换封面' : '上传封面'}
              </button>
            ) : (
              <p className="section-hint">保存后即可上传封面</p>
            )}
          </div>
        </div>
        <p className="section-hint">时间窗只影响小程序读取：同一时刻有多套可用主题时，优先「当前主题」，其次开始时间最晚的一套。</p>
        <div className="form-foot">
          <button type="submit" className="button button--primary" disabled={saving}>
            {saving ? '保存中…' : themeId ? '保存主题信息' : '创建主题'}
          </button>
        </div>
      </form>

      <div className="card card--pad">
        <div className="section-head">
          <div>
            <h2 className="section-title">首页模块</h2>
            <p className="section-hint">按顺序自上而下渲染；「首屏大图」必须有一张启用且带图的模块才能设为当前主题。</p>
          </div>
          <button
            type="button"
            className="button button--primary"
            disabled={!themeId || savingModules}
            onClick={() => void saveModules()}
          >
            {savingModules ? '保存中…' : '保存模块顺序'}
          </button>
        </div>

        <div className="module-list">
          {modules.map((module, index) => (
            <div className={`module-item ${module.enabled ? '' : 'is-off'}`} key={module.key}>
              <div className="module-item__head">
                <strong>{HOME_MODULE_LABEL[module.type] ?? module.type}</strong>
                <label className="module-item__toggle">
                  <input
                    type="checkbox"
                    checked={module.enabled}
                    onChange={(e) => updateModule(module.key, { enabled: e.target.checked })}
                  />
                  在首页展示
                </label>
              </div>
              <div className="module-item__body">
                {module.type === 'COLLECTION' && (
                  <label className="field">
                    <span>选择系列 *</span>
                    <select value={module.refId} onChange={(e) => updateModule(module.key, { refId: e.target.value })}>
                      <option value="">请选择</option>
                      {collections.map((collection) => (
                        <option key={collection.id} value={collection.id}>
                          {collection.name}
                          {collection.status !== 'ON' ? `（${collection.status}）` : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {module.type === 'STORE' && (
                  <label className="field">
                    <span>指定门店</span>
                    <select value={module.refId} onChange={(e) => updateModule(module.key, { refId: e.target.value })}>
                      <option value="">默认门店（第一家）</option>
                      {stores.map((store) => (
                        <option key={store.id} value={store.id}>
                          {store.name}
                          {store.status !== 'ON' ? '（已下线，不会展示）' : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <div className="module-item__image">
                  <div
                    className="module-item__thumb"
                    style={module.imageUrl ? { backgroundImage: `url(${module.imageUrl})` } : undefined}
                  >
                    {!module.imageUrl && <span>无配图</span>}
                  </div>
                  <div className="module-item__image-actions">
                    <button
                      type="button"
                      className="link-button"
                      disabled={uploading || !themeId}
                      onClick={() => pickModuleImage(module.key)}
                    >
                      {module.imageUrl ? '更换配图' : '上传配图'}
                    </button>
                    {module.imageUrl && (
                      <button type="button" className="link-button link-button--danger" onClick={() => updateModule(module.key, { imageUrl: '' })}>
                        移除配图
                      </button>
                    )}
                  </div>
                  <p className="section-hint">
                    {module.type === 'BANNER'
                      ? '首屏大图会覆盖主题封面'
                      : module.type === 'STORE'
                        ? '可选；门店卡封面在「门店」页维护'
                        : module.type === 'LOOK'
                          ? '可选；造型卡封面在「造型维护」里维护'
                          : '可选，用于模块头图'}
                  </p>
                </div>
              </div>
              <div className="module-item__actions">
                <button type="button" className="link-button" disabled={index === 0} onClick={() => moveModule(index, -1)}>
                  上移
                </button>
                <button
                  type="button"
                  className="link-button"
                  disabled={index === modules.length - 1}
                  onClick={() => moveModule(index, 1)}
                >
                  下移
                </button>
                <button type="button" className="link-button link-button--danger" onClick={() => removeModule(module.key)}>
                  移除
                </button>
              </div>
            </div>
          ))}
          {modules.length === 0 && <p className="empty">还没有模块，从下方添加</p>}
        </div>

        <div className="module-add">
          {MODULE_TYPES.map((type) => (
            <button key={type} type="button" className="button" disabled={!themeId} onClick={() => addModule(type)}>
              + {HOME_MODULE_LABEL[type]}
            </button>
          ))}
        </div>

        <input
          ref={coverRef}
          type="file"
          accept="image/jpeg,image/png"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) void uploadCover(file)
          }}
        />
        <input
          ref={moduleImageRef}
          type="file"
          accept="image/jpeg,image/png"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) void uploadModuleImage(file)
          }}
        />
      </div>
    </section>
  )
}

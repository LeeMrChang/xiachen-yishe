import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import {
  STATUS_LABEL,
  dateTime,
  yuan,
  type LookDetail,
  type PageResult,
  type ProductRow,
  type UploadResult,
} from '@/api/types'

interface FormState {
  title: string
  description: string
  sortNo: string
}

interface CoverState {
  url: string
  width: number
  height: number
}

interface ItemState {
  productId: number
  title: string
  coverUrl: string
  priceCent: number
  status: string
}

const EMPTY_FORM: FormState = { title: '', description: '', sortNo: '0' }
const EMPTY_COVER: CoverState = { url: '', width: 0, height: 0 }

export default function LookEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const lookId = id ? Number(id) : null
  const fileRef = useRef<HTMLInputElement>(null)

  const [detail, setDetail] = useState<LookDetail | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [cover, setCover] = useState<CoverState>(EMPTY_COVER)
  const [items, setItems] = useState<ItemState[]>([])
  const [products, setProducts] = useState<ProductRow[]>([])
  const [picker, setPicker] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [savingItems, setSavingItems] = useState(false)
  const [uploading, setUploading] = useState(false)

  const applyDetail = useCallback((look: LookDetail) => {
    setDetail(look)
    setForm({ title: look.title, description: look.description ?? '', sortNo: String(look.sortNo) })
    setCover({ url: look.coverUrl, width: look.coverWidth, height: look.coverHeight })
    setItems(
      look.items.map((item) => ({
        productId: item.productId,
        title: item.title,
        coverUrl: item.coverUrl,
        priceCent: item.priceCent,
        status: item.status,
      }))
    )
  }, [])

  const load = useCallback(async (targetId: number) => {
    setLoading(true)
    setError('')
    try {
      applyDetail(await api<LookDetail>(`/api/admin/looks/${targetId}`))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载造型失败')
    } finally {
      setLoading(false)
    }
  }, [applyDetail])

  useEffect(() => {
    if (lookId) {
      void load(lookId)
    } else {
      setDetail(null)
      setForm(EMPTY_FORM)
      setCover(EMPTY_COVER)
      setItems([])
    }
  }, [lookId, load])

  useEffect(() => {
    api<PageResult<ProductRow>>('/api/admin/products?status=ON&page=1&pageSize=100')
      .then((result) => setProducts(result.list))
      .catch(() => setProducts([]))
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
    if (!form.title.trim()) {
      setError('请填写造型标题')
      return
    }
    const body = JSON.stringify({
      title: form.title.trim(),
      description: form.description,
      coverUrl: cover.url,
      coverWidth: cover.width,
      coverHeight: cover.height,
      sortNo: Number(form.sortNo) || 0,
    })
    setSaving(true)
    try {
      if (lookId) {
        applyDetail(await api<LookDetail>(`/api/admin/looks/${lookId}`, { method: 'PUT', body }))
        flash('造型信息已保存')
      } else {
        const created = await api<LookDetail>('/api/admin/looks', { method: 'POST', body })
        flash('已创建草稿，继续添加单品')
        navigate(`/looks/${created.id}`, { replace: true })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function uploadCover(file: File) {
    if (!lookId) return
    setUploading(true)
    setError('')
    try {
      const payload = new FormData()
      payload.append('file', file)
      const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
      applyDetail(
        await api<LookDetail>(`/api/admin/looks/${lookId}`, {
          method: 'PUT',
          body: JSON.stringify({
            title: form.title.trim(),
            description: form.description,
            coverUrl: uploaded.url,
            coverWidth: uploaded.width,
            coverHeight: uploaded.height,
            sortNo: Number(form.sortNo) || 0,
          }),
        })
      )
      flash('封面已更新')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '上传失败')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  function addItem(product: ProductRow) {
    if (items.length >= 12) {
      setError('一套造型最多 12 件单品')
      return
    }
    if (items.some((item) => item.productId === product.id)) {
      setError('这件商品已经在造型里了')
      return
    }
    setError('')
    setItems((prev) => [
      ...prev,
      {
        productId: product.id,
        title: product.title,
        coverUrl: product.coverUrl,
        priceCent: product.priceCent,
        status: product.status,
      },
    ])
  }

  function moveItem(index: number, delta: number) {
    setItems((prev) => {
      const next = [...prev]
      const target = index + delta
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  function removeItem(productId: number) {
    setItems((prev) => prev.filter((item) => item.productId !== productId))
  }

  async function saveItems() {
    if (!lookId) return
    setError('')
    if (items.length === 0) {
      setError('至少保留一件单品')
      return
    }
    setSavingItems(true)
    try {
      applyDetail(
        await api<LookDetail>(`/api/admin/looks/${lookId}/items`, {
          method: 'PUT',
          body: JSON.stringify({ productIds: items.map((item) => item.productId) }),
        })
      )
      flash('单品顺序已保存')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '单品保存失败')
    } finally {
      setSavingItems(false)
    }
  }

  async function changeStatus(action: 'publish' | 'offline') {
    if (!lookId) return
    if (action === 'offline' && !window.confirm('下线后小程序首页不再展示该造型，确认？')) return
    setError('')
    try {
      applyDetail(await api<LookDetail>(`/api/admin/looks/${lookId}/${action}`, { method: 'POST' }))
      flash(action === 'publish' ? '已发布' : '已下线')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '操作失败')
    }
  }

  const filtered = useMemo(() => {
    const keyword = picker.trim()
    const chosen = new Set(items.map((item) => item.productId))
    return products.filter(
      (product) => !chosen.has(product.id) && (!keyword || product.title.includes(keyword))
    )
  }, [picker, products, items])

  const totalPrice = items.reduce((sum, item) => sum + item.priceCent, 0)

  return (
    <section>
      <header className="page-head">
        <div>
          <p className="crumb">
            <Link to="/looks">造型维护</Link> / {lookId ? `#${lookId}` : '新建'}
          </p>
          <h1 className="page-title">{lookId ? form.title || '造型编辑' : '新建造型'}</h1>
          <p className="page-desc">
            {detail ? (
              <>
                <span className={`chip chip--${detail.status.toLowerCase()}`}>
                  {STATUS_LABEL[detail.status] ?? detail.status}
                </span>
                <span className="page-desc__sep">更新于 {dateTime(detail.updatedAt)}</span>
              </>
            ) : (
              '先保存基本信息与封面，再从在售商品里挑 3 件左右组成一套'
            )}
          </p>
        </div>
        <div className="page-head__actions">
          {lookId && detail && (
            detail.status === 'ON' ? (
              <button type="button" className="button" onClick={() => void changeStatus('offline')}>
                下线
              </button>
            ) : (
              <button type="button" className="button button--primary" onClick={() => void changeStatus('publish')}>
                发布造型
              </button>
            )
          )}
        </div>
      </header>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}
      {loading && <p className="empty">加载中…</p>}

      <form className="card card--pad" onSubmit={saveBasics}>
        <h2 className="section-title">基本信息</h2>
        <div className="look-cover">
          <div className="look-cover__preview">
            {cover.url ? <img src={cover.url} alt="" /> : <span className="thumb thumb--empty">暂无封面</span>}
          </div>
          <div className="look-cover__side">
            <p className="section-hint">封面用于首页造型灵感卡片，建议 3:4 竖图。</p>
            {lookId ? (
              <button
                type="button"
                className="button"
                disabled={uploading}
                onClick={() => fileRef.current?.click()}
              >
                {uploading ? '上传中…' : cover.url ? '更换封面' : '上传封面'}
              </button>
            ) : (
              <p className="empty">保存草稿后即可上传封面</p>
            )}
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) void uploadCover(file)
              }}
            />
          </div>
        </div>
        <div className="grid-2">
          <label className="field">
            <span>造型标题 *</span>
            <input value={form.title} onChange={(e) => setField('title', e.target.value)} maxLength={128} />
          </label>
          <label className="field">
            <span>排序</span>
            <input
              type="number"
              min={0}
              value={form.sortNo}
              onChange={(e) => setField('sortNo', e.target.value)}
            />
          </label>
        </div>
        <label className="field">
          <span>造型描述</span>
          <textarea
            rows={3}
            maxLength={5000}
            value={form.description}
            onChange={(e) => setField('description', e.target.value)}
            placeholder="这套怎么穿、适合什么场合"
          />
        </label>
        <div className="form-foot">
          <button type="submit" className="button button--primary" disabled={saving}>
            {saving ? '保存中…' : lookId ? '保存基本信息' : '创建草稿'}
          </button>
        </div>
      </form>

      <div className="card card--pad">
        <div className="section-head">
          <div>
            <h2 className="section-title">造型单品</h2>
            <p className="section-hint">
              共 {items.length} 件 · 合计 {yuan(totalPrice)}；顺序即小程序展示顺序
            </p>
          </div>
          <button type="button" className="button button--primary" onClick={() => void saveItems()} disabled={!lookId || savingItems || items.length === 0}>
            {savingItems ? '保存中…' : '保存单品'}
          </button>
        </div>

        <div className="look-items">
          {items.map((item, index) => (
            <figure className="look-item" key={item.productId}>
              {item.coverUrl ? <img src={item.coverUrl} alt="" /> : <span className="thumb thumb--empty">无图</span>}
              <figcaption>
                <strong>{item.title}</strong>
                <span>{yuan(item.priceCent)}</span>
                {item.status !== 'ON' && <span className="text-warn">已下架</span>}
              </figcaption>
              <div className="look-item__actions">
                <button type="button" className="link-button" disabled={index === 0} onClick={() => moveItem(index, -1)}>
                  ↑
                </button>
                <button
                  type="button"
                  className="link-button"
                  disabled={index === items.length - 1}
                  onClick={() => moveItem(index, 1)}
                >
                  ↓
                </button>
                <button type="button" className="link-button link-button--danger" onClick={() => removeItem(item.productId)}>
                  移除
                </button>
              </div>
            </figure>
          ))}
          {items.length === 0 && <p className="empty">还没有单品，从下方商品里挑几件</p>}
        </div>

        <div className="look-picker">
          <div className="section-head">
            <h3 className="section-title">从在售商品添加</h3>
            <input
              className="toolbar__search"
              placeholder="搜索商品标题"
              value={picker}
              onChange={(e) => setPicker(e.target.value)}
              disabled={!lookId}
            />
          </div>
          <div className="look-picker__grid">
            {filtered.slice(0, 12).map((product) => (
              <button
                type="button"
                className="look-pick"
                key={product.id}
                onClick={() => addItem(product)}
                disabled={!lookId}
              >
                {product.coverUrl ? <img src={product.coverUrl} alt="" /> : <span className="thumb thumb--empty">无图</span>}
                <span>{product.title}</span>
                <small>{yuan(product.priceCent)}</small>
              </button>
            ))}
            {filtered.length === 0 && <p className="empty">没有可添加的商品</p>}
          </div>
        </div>
      </div>
    </section>
  )
}

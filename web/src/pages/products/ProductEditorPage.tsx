import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '@/api/client'
import {
  STATUS_LABEL,
  dateTime,
  yuan,
  type ProductDetail,
  type UploadResult,
} from '@/api/types'

interface FormState {
  title: string
  subtitle: string
  category: string
  season: string
  material: string
  description: string
  sortWeight: string
}

interface SkuDraft {
  key: string
  color: string
  size: string
  price: string
  originPrice: string
}

const EMPTY_FORM: FormState = {
  title: '',
  subtitle: '',
  category: '',
  season: '',
  material: '',
  description: '',
  sortWeight: '0',
}

function newKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random()}`
}

function draftOf(product: ProductDetail): SkuDraft[] {
  return product.skus
    .filter((s) => s.status === 'ON')
    .map((s) => ({
      key: newKey(),
      color: s.color,
      size: s.size,
      price: (s.priceCent / 100).toFixed(2),
      originPrice: s.originPriceCent > 0 ? (s.originPriceCent / 100).toFixed(2) : '',
    }))
}

export default function ProductEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const productId = id ? Number(id) : null
  const fileRef = useRef<HTMLInputElement>(null)

  const [detail, setDetail] = useState<ProductDetail | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [skus, setSkus] = useState<SkuDraft[]>([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [savingSkus, setSavingSkus] = useState(false)
  const [uploading, setUploading] = useState(false)

  const load = useCallback(async (targetId: number) => {
    setLoading(true)
    setError('')
    try {
      const product = await api<ProductDetail>(`/api/admin/products/${targetId}`)
      setDetail(product)
      setForm({
        title: product.title,
        subtitle: product.subtitle,
        category: product.category,
        season: product.season,
        material: product.material,
        description: product.description ?? '',
        sortWeight: String(product.sortWeight),
      })
      setSkus(draftOf(product))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (productId) {
      void load(productId)
    } else {
      setDetail(null)
      setForm(EMPTY_FORM)
      setSkus([])
    }
  }, [productId, load])

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
      setError('请填写商品标题')
      return
    }
    setSaving(true)
    const body = JSON.stringify({
      title: form.title,
      subtitle: form.subtitle,
      category: form.category,
      season: form.season,
      material: form.material,
      description: form.description,
      sortWeight: Number(form.sortWeight) || 0,
    })
    try {
      if (productId) {
        const product = await api<ProductDetail>(`/api/admin/products/${productId}`, { method: 'PUT', body })
        setDetail(product)
        flash('基本信息已保存')
      } else {
        const product = await api<ProductDetail>('/api/admin/products', { method: 'POST', body })
        flash('已创建草稿，继续上传图片和 SKU')
        navigate(`/products/${product.id}`, { replace: true })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function uploadImage(file: File) {
    if (!productId) return
    setUploading(true)
    setError('')
    try {
      const payload = new FormData()
      payload.append('file', file)
      const uploaded = await api<UploadResult>('/api/admin/uploads', { method: 'POST', body: payload })
      const product = await api<ProductDetail>(`/api/admin/products/${productId}/images`, {
        method: 'POST',
        body: JSON.stringify({ url: uploaded.url, width: uploaded.width, height: uploaded.height }),
      })
      setDetail(product)
      flash('图片已上传')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '上传失败')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function bindCover(imageId: number) {
    if (!productId) return
    setError('')
    try {
      setDetail(await api<ProductDetail>(`/api/admin/products/${productId}/images/${imageId}/cover`, { method: 'POST' }))
      flash('已设为主图')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '设置失败')
    }
  }

  async function removeImage(imageId: number) {
    if (!productId || !window.confirm('确认删除这张图片？')) return
    setError('')
    try {
      setDetail(await api<ProductDetail>(`/api/admin/products/${productId}/images/${imageId}`, { method: 'DELETE' }))
      flash('图片已删除')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '删除失败')
    }
  }

  function updateSku(key: string, patch: Partial<SkuDraft>) {
    setSkus((prev) => prev.map((sku) => (sku.key === key ? { ...sku, ...patch } : sku)))
  }

  function addSku() {
    setSkus((prev) => [...prev, { key: newKey(), color: '', size: '', price: '', originPrice: '' }])
  }

  function removeSku(key: string) {
    setSkus((prev) => prev.filter((sku) => sku.key !== key))
  }

  async function saveSkus() {
    if (!productId) return
    setError('')
    if (skus.length === 0) {
      setError('至少保留一个 SKU')
      return
    }
    const items = []
    for (const sku of skus) {
      const price = Number(sku.price)
      if (!sku.color.trim() || !sku.size.trim()) {
        setError('每个 SKU 都要填写颜色和尺码')
        return
      }
      if (!Number.isFinite(price) || price <= 0) {
        setError(`「${sku.color} / ${sku.size}」的售价不正确`)
        return
      }
      items.push({
        color: sku.color.trim(),
        size: sku.size.trim(),
        priceCent: Math.round(price * 100),
        originPriceCent: sku.originPrice ? Math.round(Number(sku.originPrice) * 100) : 0,
      })
    }
    setSavingSkus(true)
    try {
      const product = await api<ProductDetail>(`/api/admin/products/${productId}/skus`, {
        method: 'PUT',
        body: JSON.stringify({ items }),
      })
      setDetail(product)
      setSkus(draftOf(product))
      flash('SKU 已保存')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'SKU 保存失败')
    } finally {
      setSavingSkus(false)
    }
  }

  async function changeStatus(action: 'publish' | 'offline') {
    if (!productId) return
    if (action === 'offline' && !window.confirm('下线后小程序将不再展示该商品，确认？')) return
    setError('')
    try {
      setDetail(await api<ProductDetail>(`/api/admin/products/${productId}/${action}`, { method: 'POST' }))
      flash(action === 'publish' ? '已发布' : '已下线')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '操作失败')
    }
  }

  const pricePreview = useMemo(() => {
    const prices = skus.map((s) => Number(s.price)).filter((p) => Number.isFinite(p) && p > 0)
    return prices.length ? `起售 ${yuan(Math.min(...prices) * 100)}` : '未定价'
  }, [skus])

  return (
    <section>
      <header className="page-head">
        <div>
          <p className="crumb">
            <Link to="/products">商品维护</Link> / {productId ? `#${productId}` : '新建'}
          </p>
          <h1 className="page-title">{productId ? form.title || '商品编辑' : '新建商品'}</h1>
          <p className="page-desc">
            {detail ? (
              <>
                <span className={`chip chip--${detail.status.toLowerCase()}`}>
                  {STATUS_LABEL[detail.status] ?? detail.status}
                </span>
                <span className="page-desc__sep">更新于 {dateTime(detail.updatedAt)}</span>
              </>
            ) : (
              '先保存基本信息，再上传图片、维护 SKU 与门店库存'
            )}
          </p>
        </div>
        <div className="page-head__actions">
          {productId && detail && (
            <>
              <Link className="button" to={`/inventory?productId=${productId}`}>
                库存维护
              </Link>
              {detail.status === 'ON' ? (
                <button type="button" className="button" onClick={() => void changeStatus('offline')}>
                  下线
                </button>
              ) : (
                <button type="button" className="button button--primary" onClick={() => void changeStatus('publish')}>
                  发布上架
                </button>
              )}
            </>
          )}
        </div>
      </header>

      {error && <p className="form-error">{error}</p>}
      {notice && <p className="form-notice">{notice}</p>}
      {loading && <p className="empty">加载中…</p>}

      <form className="card card--pad" onSubmit={saveBasics}>
        <h2 className="section-title">基本信息</h2>
        <div className="grid-2">
          <label className="field">
            <span>商品标题 *</span>
            <input value={form.title} onChange={(e) => setField('title', e.target.value)} maxLength={128} />
          </label>
          <label className="field">
            <span>副标题</span>
            <input value={form.subtitle} onChange={(e) => setField('subtitle', e.target.value)} maxLength={255} />
          </label>
          <label className="field">
            <span>分类</span>
            <input
              list="category-options"
              value={form.category}
              onChange={(e) => setField('category', e.target.value)}
              placeholder="上装 / 下装 / 连衣裙…"
            />
            <datalist id="category-options">
              <option value="上装" />
              <option value="下装" />
              <option value="连衣裙" />
              <option value="外套" />
              <option value="配饰" />
            </datalist>
          </label>
          <label className="field">
            <span>季节</span>
            <select value={form.season} onChange={(e) => setField('season', e.target.value)}>
              <option value="">不标注</option>
              <option value="SS">春夏 SS</option>
              <option value="AW">秋冬 AW</option>
              <option value="ALL">四季 ALL</option>
            </select>
          </label>
          <label className="field">
            <span>面料</span>
            <input value={form.material} onChange={(e) => setField('material', e.target.value)} maxLength={64} />
          </label>
          <label className="field">
            <span>排序权重</span>
            <input
              type="number"
              min={0}
              value={form.sortWeight}
              onChange={(e) => setField('sortWeight', e.target.value)}
            />
          </label>
        </div>
        <label className="field">
          <span>商品描述</span>
          <textarea rows={4} value={form.description} onChange={(e) => setField('description', e.target.value)} />
        </label>
        <div className="form-foot">
          <button type="submit" className="button button--primary" disabled={saving}>
            {saving ? '保存中…' : productId ? '保存基本信息' : '创建草稿'}
          </button>
        </div>
      </form>

      <div className="card card--pad">
        <h2 className="section-title">商品图片</h2>
        <p className="section-hint">第一张自动作为主图，可随时调整；上传时自动读取宽高供小程序瀑布流使用。</p>
        {productId ? (
          <>
            <div className="image-grid">
              {detail?.images.map((image) => (
                <figure className="image-item" key={image.id}>
                  <img src={image.url} alt="" />
                  <figcaption>
                    {detail.coverUrl === image.url ? (
                      <span className="chip chip--on">主图</span>
                    ) : (
                      <button type="button" className="link-button" onClick={() => void bindCover(image.id)}>
                        设为主图
                      </button>
                    )}
                    <button type="button" className="link-button link-button--danger" onClick={() => void removeImage(image.id)}>
                      删除
                    </button>
                  </figcaption>
                </figure>
              ))}
              <button
                type="button"
                className="image-add"
                disabled={uploading}
                onClick={() => fileRef.current?.click()}
              >
                {uploading ? '上传中…' : '+ 上传图片'}
              </button>
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) void uploadImage(file)
              }}
            />
          </>
        ) : (
          <p className="empty">保存草稿后即可上传图片</p>
        )}
      </div>

      <div className="card card--pad">
        <div className="section-head">
          <div>
            <h2 className="section-title">SKU 与定价</h2>
            <p className="section-hint">{pricePreview}；删除的行在保存后会置为停用，历史库存保留。</p>
          </div>
          <button type="button" className="button" onClick={addSku} disabled={!productId}>
            添加 SKU
          </button>
        </div>
        <table className="table table--compact">
          <thead>
            <tr>
              <th>颜色</th>
              <th>尺码</th>
              <th>售价（元）</th>
              <th>划线价（元）</th>
              <th>门店库存</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {skus.map((sku) => {
              const saved = detail?.skus.find(
                (s) => s.status === 'ON' && s.color === sku.color && s.size === sku.size
              )
              return (
                <tr key={sku.key}>
                  <td>
                    <input
                      value={sku.color}
                      maxLength={32}
                      onChange={(e) => updateSku(sku.key, { color: e.target.value })}
                      placeholder="烟灰"
                    />
                  </td>
                  <td>
                    <input
                      value={sku.size}
                      maxLength={16}
                      onChange={(e) => updateSku(sku.key, { size: e.target.value })}
                      placeholder="M"
                    />
                  </td>
                  <td>
                    <input
                      inputMode="decimal"
                      value={sku.price}
                      onChange={(e) => updateSku(sku.key, { price: e.target.value })}
                      placeholder="599.00"
                    />
                  </td>
                  <td>
                    <input
                      inputMode="decimal"
                      value={sku.originPrice}
                      onChange={(e) => updateSku(sku.key, { originPrice: e.target.value })}
                      placeholder="选填"
                    />
                  </td>
                  <td className="text-muted">{saved ? saved.totalQuantity : '新'}</td>
                  <td className="table__actions">
                    <button type="button" className="link-button link-button--danger" onClick={() => removeSku(sku.key)}>
                      移除
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {skus.length === 0 && <p className="empty">还没有 SKU，点击右上角添加</p>}
        <div className="form-foot">
          <button type="button" className="button button--primary" onClick={() => void saveSkus()} disabled={!productId || savingSkus}>
            {savingSkus ? '保存中…' : '保存 SKU'}
          </button>
        </div>
      </div>
    </section>
  )
}

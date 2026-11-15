export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
  hasMore: boolean
}

export interface ProductRow {
  id: number
  title: string
  coverUrl: string
  category: string
  status: string
  priceCent: number
  totalQuantity: number
  skuCount: number
  onlineAt: string | null
  updatedAt: string | null
}

export interface ProductImage {
  id: number
  url: string
  width: number
  height: number
  sortNo: number
}

export interface ProductSku {
  id: number
  color: string
  size: string
  priceCent: number
  originPriceCent: number
  sortNo: number
  status: string
  totalQuantity: number
}

export interface ProductDetail {
  id: number
  title: string
  subtitle: string
  category: string
  season: string
  material: string
  description: string | null
  coverUrl: string
  coverWidth: number
  coverHeight: number
  status: string
  sortWeight: number
  likeCount: number
  favCount: number
  onlineAt: string | null
  updatedAt: string | null
  images: ProductImage[]
  skus: ProductSku[]
}

export interface UploadResult {
  url: string
  width: number
  height: number
}

export interface InventoryMatrix {
  productId: number
  title: string
  skus: { id: number; color: string; size: string; status: string }[]
  stores: { id: number; name: string }[]
  cells: { skuId: number; storeId: number; quantity: number; onSale: boolean }[]
}

export interface StoreCover {
  id: number
  name: string
  code: string
  address: string
  phone: string
  openTime: string | null
  closeTime: string | null
  coverUrl: string
  status: string
}

export interface StoreOption {
  id: number
  name: string
  address: string
}

export interface AppointmentRow {
  id: number
  visitDate: string
  visitTime: string | null
  status: string
  statusText: string
  storeId: number
  storeName: string
  itemCount: number
  phone: string
  contactName: string
  remark: string
  createdAt: string | null
}

export interface AppointmentDetail extends AppointmentRow {
  storeAddress: string
  storePhone: string
  items: {
    skuId: number
    productId: number
    title: string
    color: string
    size: string
    coverUrl: string
    priceCent: number
  }[]
}

export interface LookRow {
  id: number
  title: string
  description: string
  coverUrl: string
  coverWidth: number
  coverHeight: number
  status: string
  sortNo: number
  itemCount: number
  updatedAt: string | null
}

export interface LookDetail {
  id: number
  title: string
  description: string
  coverUrl: string
  coverWidth: number
  coverHeight: number
  status: string
  sortNo: number
  updatedAt: string | null
  items: {
    productId: number
    title: string
    coverUrl: string
    priceCent: number
    status: string
    sortNo: number
  }[]
}

export interface CollectionOption {
  id: number
  name: string
  status: string
}

export interface CollectionRow {
  id: number
  name: string
  coverUrl: string
  story: string
  publishAt: string | null
  status: string
  sortNo: number
  productCount: number
  updatedAt: string | null
}

export interface CollectionDetail {
  id: number
  name: string
  coverUrl: string
  story: string
  publishAt: string | null
  status: string
  sortNo: number
  updatedAt: string | null
  products: {
    productId: number
    title: string
    coverUrl: string
    priceCent: number
    status: string
    sortNo: number
  }[]
}

export interface HomeThemeRow {
  id: number
  name: string
  coverUrl: string
  copyText: string
  startAt: string | null
  endAt: string | null
  status: string
  current: boolean
  moduleCount: number
  updatedAt: string | null
}

export interface HomeModuleItem {
  id: number
  type: string
  typeLabel: string
  refId: number | null
  refName: string | null
  imageUrl: string
  sortNo: number
  enabled: boolean
}

export interface HomeThemeDetail {
  id: number
  name: string
  coverUrl: string
  copyText: string
  startAt: string | null
  endAt: string | null
  status: string
  current: boolean
  updatedAt: string | null
  modules: HomeModuleItem[]
}

export const HOME_MODULE_LABEL: Record<string, string> = {
  BANNER: '首屏大图',
  RECOMMEND: '店长推荐',
  COLLECTION: '系列',
  LOOK: '造型灵感',
  STORE: '门店',
}

export function themeStatusLabel(row: { status: string; current: boolean }): string {
  if (row.current) return '当前主题'
  return STATUS_LABEL[row.status] ?? row.status
}

export const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ON: '在售',
  OFF: '已下线',
}

export const APPOINTMENT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  DONE: '已到店',
  CANCELED: '已取消',
}

export function yuan(cent: number): string {
  return `¥${(cent / 100).toFixed(2)}`
}

export function dateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function clock(value: string | null): string {
  if (!value) return '时间待定'
  return value.slice(0, 5)
}

export function todayKey(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

const { request } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const PAGE_SIZE = 10

function statusClass(status) {
  return String(status || '').toLowerCase()
}

function mapAppointment(row) {
  return Object.assign({}, row, {
    statusClass: statusClass(row.status),
    storeShort: fmt.shortStoreName(row.storeName),
    visitText: fmt.visitLabel(row.visitDate, row.visitTime),
    createdText: fmt.dateText(fmt.dateKey(row.createdAt)),
    canCancel: row.status === 'PENDING' || row.status === 'CONFIRMED',
    items: (row.items || []).map(item => Object.assign({}, item, {
      priceText: fmt.formatPrice(item.priceCent)
    }))
  })
}

Page({
  data: {
    statusBarHeight: 20,
    loading: true,
    loggedIn: false,
    busy: false,
    items: [],
    page: 1,
    total: 0,
    hasMore: false,
    loadingMore: false
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
  },

  onShow() {
    if (!auth.currentToken()) {
      this.setData({ loggedIn: false, items: [], total: 0, hasMore: false, loading: false })
      return
    }
    this.load(1)
  },

  onPullDownRefresh() {
    this.load(1).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.load(this.data.page + 1)
    }
  },

  async load(page) {
    const append = page > 1
    this.setData(append ? { loadingMore: true } : { loading: true })
    try {
      const result = await request({
        url: `/api/v1/appointments?page=${page}&pageSize=${PAGE_SIZE}`,
        auth: true
      })
      const fresh = result.list.map(mapAppointment)
      this.setData({
        loggedIn: true,
        items: append ? this.data.items.concat(fresh) : fresh,
        page,
        total: result.total,
        hasMore: result.hasMore,
        loading: false,
        loadingMore: false
      })
    } catch (e) {
      this.setData({ loggedIn: Boolean(auth.currentToken()), loading: false, loadingMore: false })
    }
  },

  loadMore() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.load(this.data.page + 1)
    }
  },

  async login() {
    if (this.data.busy) {
      return
    }
    this.setData({ busy: true })
    try {
      await auth.login()
      await this.load(1)
    } catch (e) {
      // 失败原因由 request 统一提示
    } finally {
      this.setData({ busy: false })
    }
  },

  cancel(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '取消预约',
      content: '取消后如需到店请重新预约，确定取消吗？',
      success: async res => {
        if (!res.confirm) {
          return
        }
        try {
          await auth.withAuth(() => request({
            url: `/api/v1/appointments/${id}`,
            method: 'PATCH',
            auth: true
          }))
          wx.showToast({ title: '已取消', icon: 'none' })
          await this.load(1)
        } catch (err) {
          // 失败原因由 request 统一提示
        }
      }
    })
  },

  openProduct(e) {
    const productId = e.currentTarget.dataset.pid
    const storeId = e.currentTarget.dataset.store
    if (!productId) {
      return
    }
    wx.navigateTo({ url: `/pages/product/index?id=${productId}&storeId=${storeId}` })
  },

  goBook() {
    wx.switchTab({ url: '/pages/tryon-list/index' })
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/mine/index' })
  }
})

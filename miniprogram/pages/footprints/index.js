const { request } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

function stockText(status, quantity) {
  if (status === 'OUT') {
    return '本店售罄'
  }
  if (status === 'LOW') {
    return `仅剩 ${quantity} 件`
  }
  return status ? '有货' : ''
}

Page({
  data: {
    statusBarHeight: 20,
    loading: true,
    loggedIn: false,
    busy: false,
    groups: [],
    total: 0
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
  },

  onShow() {
    if (!auth.currentToken()) {
      this.setData({ loggedIn: false, groups: [], total: 0, loading: false })
      return
    }
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true })
    try {
      const items = await request({ url: '/api/v1/footprints?limit=50', auth: true })
      const groups = []
      const byDay = {}
      items.forEach(item => {
        const key = fmt.dateKey(item.viewedAt)
        if (!byDay[key]) {
          byDay[key] = { key, label: fmt.dayLabel(key), items: [] }
          groups.push(byDay[key])
        }
        byDay[key].items.push(Object.assign({}, item, {
          key: `${item.productId}-${item.storeId}`,
          priceText: item.priceCent === null || item.priceCent === undefined ? '' : fmt.formatPrice(item.priceCent),
          stockLabel: stockText(item.stockStatus, item.stockQuantity),
          storeShort: fmt.shortStoreName(item.storeName),
          timeText: fmt.timeText(item.viewedAt)
        }))
      })
      this.setData({ loggedIn: true, groups, total: items.length, loading: false })
    } catch (e) {
      this.setData({ loggedIn: Boolean(auth.currentToken()), loading: false })
    }
  },

  async login() {
    if (this.data.busy) {
      return
    }
    this.setData({ busy: true })
    try {
      await auth.login()
      await this.load()
    } catch (e) {
      // 失败原因由 request 统一提示
    } finally {
      this.setData({ busy: false })
    }
  },

  openItem(e) {
    const group = this.data.groups[e.currentTarget.dataset.g]
    const item = group && group.items[e.currentTarget.dataset.i]
    if (!item) {
      return
    }
    const store = item.storeId ? `&storeId=${item.storeId}` : ''
    wx.navigateTo({ url: `/pages/product/index?id=${item.productId}${store}` })
  },

  clearAll() {
    wx.showModal({
      title: '清空浏览足迹',
      content: '清空后不可恢复，确定继续吗？',
      success: async res => {
        if (!res.confirm) {
          return
        }
        try {
          await auth.withAuth(() => request({ url: '/api/v1/footprints', method: 'DELETE', auth: true }))
          wx.showToast({ title: '已清空', icon: 'none' })
          await this.load()
        } catch (e) {
          // 失败原因由 request 统一提示
        }
      }
    })
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/index' })
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/mine/index' })
  }
})

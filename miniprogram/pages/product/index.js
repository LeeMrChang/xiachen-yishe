const { request, formatPrice } = require('../../utils/request')
const { withAuth } = require('../../utils/auth')

Page({
  data: {
    statusBarHeight: 20,
    loading: true,
    detail: null,
    priceText: '',
    originText: '',
    colors: [],
    sizes: [],
    color: '',
    size: '',
    sku: null,
    currentIndex: 0,
    currentStoreId: null,
    currentStoreName: '',
    favorited: false,
    liked: false,
    inCart: false,
    favoriteBusy: false,
    likeBusy: false,
    tryonBusy: false
  },

  onLoad(options) {
    this.productId = Number(options.id)
    this.storeId = Number(options.storeId) || getApp().globalData.storeId || null
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
    this.load()
  },

  async load() {
    try {
      const query = this.storeId ? `?storeId=${this.storeId}` : ''
      const detail = await request({ url: `/api/v1/products/${this.productId}${query}` })
      const colors = []
      const sizes = []
      detail.skus.forEach(sku => {
        if (!colors.includes(sku.color)) {
          colors.push(sku.color)
        }
        if (!sizes.includes(sku.size)) {
          sizes.push(sku.size)
        }
      })
      const sku = detail.skus.find(item => item.stockStatus !== 'OUT') || detail.skus[0] || null
      const currentStore = detail.stores.find(item => item.storeId === this.storeId) || detail.stores[0] || null
      if (currentStore) {
        this.storeId = currentStore.storeId
        getApp().globalData.storeId = currentStore.storeId
      }
      this.setData({
        detail,
        colors,
        sizes,
        color: sku ? sku.color : '',
        size: sku ? sku.size : '',
        sku,
        priceText: formatPrice(sku ? sku.priceCent : detail.priceCent),
        originText: detail.originPriceCent ? formatPrice(detail.originPriceCent) : '',
        currentStoreId: currentStore ? currentStore.storeId : null,
        currentStoreName: currentStore ? currentStore.storeName : '',
        favorited: Boolean(detail.favorited),
        liked: Boolean(detail.liked),
        inCart: Boolean(detail.inCart),
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  selectSku(color, size) {
    const skus = this.data.detail.skus
    let sku = skus.find(item => item.color === color && item.size === size)
    if (!sku) {
      sku = skus.find(item => item.color === color) || skus.find(item => item.size === size) || this.data.sku
    }
    this.setData({
      color: sku ? sku.color : color,
      size: sku ? sku.size : size,
      sku,
      priceText: formatPrice(sku ? sku.priceCent : this.data.detail.priceCent)
    })
  },

  onColorTap(e) {
    this.selectSku(e.currentTarget.dataset.value, this.data.size)
  },

  onSizeTap(e) {
    this.selectSku(this.data.color, e.currentTarget.dataset.value)
  },

  onSwiperChange(e) {
    this.setData({ currentIndex: e.detail.current })
  },

  onFavoriteTap() {
    this.toggleMark('favorites', 'favorited', 'favoriteBusy', 'favCount')
  },

  onLikeTap() {
    this.toggleMark('likes', 'liked', 'likeBusy', 'likeCount')
  },

  async toggleMark(kind, activeKey, busyKey, countKey) {
    const detail = this.data.detail
    if (!detail || this.data[busyKey]) {
      return
    }
    const next = !this.data[activeKey]
    const prevCount = Number(detail[countKey] || 0)
    const base = kind === 'favorites' ? '/api/v1/favorites' : '/api/v1/likes'
    this.setData({
      [activeKey]: next,
      [busyKey]: true,
      [`detail.${countKey}`]: Math.max(prevCount + (next ? 1 : -1), 0)
    })
    try {
      await withAuth(() => request({
        url: next ? base : `${base}?targetType=1&targetId=${this.productId}`,
        method: next ? 'POST' : 'DELETE',
        auth: true,
        data: next ? { targetType: 1, targetId: this.productId } : undefined
      }))
    } catch (e) {
      this.setData({
        [activeKey]: !next,
        [`detail.${countKey}`]: prevCount
      })
    } finally {
      this.setData({ [busyKey]: false })
    }
  },

  async onTryonTap() {
    const sku = this.data.sku
    if (!sku || !this.data.currentStoreId) {
      wx.showToast({ title: '先选颜色和尺码', icon: 'none' })
      return
    }
    if (this.data.tryonBusy) {
      return
    }
    this.setData({ tryonBusy: true })
    try {
      await withAuth(() => request({
        url: '/api/v1/tryon-items',
        method: 'POST',
        auth: true,
        data: { skuId: sku.id, storeId: this.data.currentStoreId }
      }))
      this.setData({ inCart: true })
      wx.showToast({ title: '已加入试穿清单', icon: 'none' })
    } catch (e) {
      // 失败原因由 request 统一提示
    } finally {
      this.setData({ tryonBusy: false })
    }
  },

  async onBookTap() {
    const sku = this.data.sku
    if (!sku || !this.data.currentStoreId) {
      wx.showToast({ title: '先选颜色和尺码', icon: 'none' })
      return
    }
    try {
      await withAuth(() => request({
        url: '/api/v1/tryon-items',
        method: 'POST',
        auth: true,
        data: { skuId: sku.id, storeId: this.data.currentStoreId }
      }))
      this.setData({ inCart: true })
      getApp().globalData.pendingBookStoreId = this.data.currentStoreId
      wx.switchTab({ url: '/pages/tryon-list/index' })
    } catch (e) {
      // 失败原因由 request 统一提示
    }
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/home/index' })
  }
})

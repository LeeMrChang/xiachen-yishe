const { request } = require('../../utils/request')

Page({
  data: {
    statusBarHeight: 20,
    loading: true,
    stores: [],
    storeId: null,
    theme: null,
    banner: '',
    modules: [],
    expanded: false,
    tabTransparent: true
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
    this.loadStores()
  },

  onShow() {
    this.syncTabBar(this.data.tabTransparent)
  },

  onPageScroll({ scrollTop }) {
    this.syncTabBar(scrollTop < 220)
  },

  onPullDownRefresh() {
    this.loadHome().finally(() => wx.stopPullDownRefresh())
  },

  syncTabBar(transparent) {
    const tabBar = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tabBar) {
      tabBar.setData({ selected: 0, transparent })
    }
    if (this.data.tabTransparent !== transparent) {
      this.setData({ tabTransparent: transparent })
    }
  },

  async loadStores() {
    try {
      const stores = await request({ url: '/api/v1/stores' })
      const list = stores.map(store => ({
        ...store,
        shortName: store.name.replace('夏晨衣舍 · ', '')
      }))
      const storeId = getApp().globalData.storeId || (list[0] && list[0].id) || null
      this.setData({ stores: list, storeId })
      getApp().globalData.storeId = storeId
      await this.loadHome()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  async loadHome() {
    if (!this.data.storeId) {
      return
    }
    if (!this.data.theme) {
      this.setData({ loading: true })
    }
    try {
      const home = await request({ url: `/api/v1/home?storeId=${this.data.storeId}` })
      const bannerModule = (home.modules || []).find(module => module.type === 'BANNER')
      this.setData({
        theme: home.theme,
        banner: (bannerModule && bannerModule.imageUrl) || (home.theme && home.theme.coverUrl) || '',
        modules: home.modules || [],
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  toggleCollection() {
    if (this.data.expanded) {
      this.setData({ expanded: false }, () => {
        wx.nextTick(() => {
          wx.pageScrollTo({ scrollTop: 0, duration: 300 })
        })
      })
      return
    }
    this.setData({ expanded: true }, () => {
      wx.nextTick(() => {
        wx.pageScrollTo({ selector: '#collection', duration: 360 })
      })
    })
  },

  onStoreChange(e) {
    const { storeId } = e.detail
    getApp().globalData.storeId = storeId
    this.setData({ storeId })
    this.loadHome()
  }
})

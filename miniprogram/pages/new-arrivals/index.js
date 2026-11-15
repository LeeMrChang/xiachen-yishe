const { request } = require('../../utils/request')

const PAGE_SIZE = 20

Page({
  data: {
    statusBarHeight: 20,
    stores: [],
    storeId: null,
    list: [],
    total: 0,
    page: 1,
    hasMore: false,
    loading: true,
    loadingMore: false
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
    this.loadStores()
  },

  onShow() {
    const tabBar = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tabBar) {
      tabBar.setData({ selected: 1, transparent: false })
    }
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.loadMore()
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
      await this.refresh()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  async refresh() {
    if (!this.data.storeId) {
      return
    }
    this.setData({ loading: true, page: 1 })
    try {
      const result = await this.fetchPage(1)
      this.setData({
        list: result.list,
        total: result.total,
        hasMore: result.hasMore,
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  async loadMore() {
    const nextPage = this.data.page + 1
    this.setData({ loadingMore: true })
    try {
      const result = await this.fetchPage(nextPage)
      this.setData({
        list: this.data.list.concat(result.list),
        page: nextPage,
        hasMore: result.hasMore,
        loadingMore: false
      })
    } catch (e) {
      this.setData({ loadingMore: false })
    }
  },

  fetchPage(page) {
    return request({
      url: `/api/v1/products?sort=new&page=${page}&pageSize=${PAGE_SIZE}&storeId=${this.data.storeId}`
    })
  },

  onStoreChange(e) {
    const { storeId } = e.detail
    getApp().globalData.storeId = storeId
    this.setData({ storeId })
    this.refresh()
  }
})

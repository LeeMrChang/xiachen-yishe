const { request, formatPrice } = require('../../utils/request')
const auth = require('../../utils/auth')

const PAGE_SIZE = 20

Page({
  data: {
    statusBarHeight: 20,
    loggedIn: false,
    busy: false,
    user: null,
    initial: '微',
    tab: 1,
    items: [],
    page: 1,
    total: 0,
    hasMore: false,
    loading: true,
    loadingMore: false
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
  },

  onShow() {
    const tabBar = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tabBar) {
      tabBar.setData({ selected: 3, transparent: false })
    }
    if (!auth.currentToken()) {
      this.setData({ loggedIn: false, user: null, items: [], loading: false })
      return
    }
    this.refresh()
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.loadList(this.data.page + 1)
    }
  },

  async refresh() {
    this.setData({ loading: true })
    const user = await auth.loadProfile()
    if (!user) {
      this.setData({
        loggedIn: false,
        user: null,
        items: [],
        total: 0,
        hasMore: false,
        loading: false
      })
      return
    }
    const nickname = user.nickname || '微信用户'
    this.setData({ loggedIn: true, user, initial: nickname.slice(0, 1) })
    await this.loadList(1)
  },

  async login() {
    if (this.data.busy) {
      return
    }
    this.setData({ busy: true })
    try {
      await auth.login()
      await this.refresh()
    } catch (e) {
      // 失败原因由 request 统一提示
    } finally {
      this.setData({ busy: false })
    }
  },

  switchTab(e) {
    const tab = Number(e.currentTarget.dataset.tab)
    if (tab === this.data.tab) {
      return
    }
    this.setData({ tab, items: [], total: 0, hasMore: false, loading: true })
    this.loadList(1)
  },

  async loadList(page) {
    const kind = this.data.tab === 1 ? 'favorites' : 'likes'
    const append = page > 1
    if (append) {
      this.setData({ loadingMore: true })
    }
    try {
      const result = await request({
        url: `/api/v1/${kind}?targetType=${this.data.tab}&page=${page}&pageSize=${PAGE_SIZE}`,
        auth: true
      })
      const fresh = result.list.map(item => Object.assign({}, item, {
        showPrice: item.priceCent !== null && item.priceCent !== undefined,
        priceText: item.priceCent === null || item.priceCent === undefined
          ? ''
          : formatPrice(item.priceCent)
      }))
      this.setData({
        items: append ? this.data.items.concat(fresh) : fresh,
        page,
        total: result.total,
        hasMore: result.hasMore,
        loading: false,
        loadingMore: false
      })
    } catch (e) {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  loadMore() {
    if (this.data.hasMore && !this.data.loadingMore) {
      this.loadList(this.data.page + 1)
    }
  },

  openItem(e) {
    const item = this.data.items[e.currentTarget.dataset.index]
    if (!item) {
      return
    }
    if (item.targetType === 2) {
      wx.showToast({ title: '造型详情下一阶段开放', icon: 'none' })
      return
    }
    const storeId = getApp().globalData.storeId
    wx.navigateTo({
      url: `/pages/product/index?id=${item.targetId}${storeId ? `&storeId=${storeId}` : ''}`
    })
  },

  onHeadTap() {
    if (this.data.loggedIn) {
      this.editNickname()
      return
    }
    this.login()
  },

  editNickname() {
    const nickname = this.data.user && this.data.user.nickname ? this.data.user.nickname : '微信用户'
    wx.showModal({
      title: '修改称呼',
      editable: true,
      placeholderText: '比如：陈小姐',
      content: `当前称呼：${nickname}`,
      success: async res => {
        if (!res.confirm) {
          return
        }
        const nickname = (res.content || '').trim()
        if (!nickname) {
          wx.showToast({ title: '称呼不能为空', icon: 'none' })
          return
        }
        try {
          const updated = await auth.updateProfile({ nickname })
          this.setData({ user: updated, initial: nickname.slice(0, 1) })
          wx.showToast({ title: '已保存', icon: 'none' })
        } catch (e) {
          // 失败原因由 request 统一提示
        }
      }
    })
  },

  async openEntry(e) {
    const url = e.currentTarget.dataset.url
    if (!url) {
      return
    }
    if (!this.data.loggedIn) {
      try {
        await auth.login()
        await this.refresh()
      } catch (err) {
        return
      }
    }
    wx.navigateTo({ url })
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '收藏和点赞会保留在账号里，下次登录还能看到',
      success: async res => {
        if (!res.confirm) {
          return
        }
        await auth.logout()
        this.setData({
          loggedIn: false,
          user: null,
          items: [],
          total: 0,
          hasMore: false,
          loading: false
        })
      }
    })
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/index' })
  }
})

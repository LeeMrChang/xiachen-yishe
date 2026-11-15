const { request } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const TIMES = ['时间待定']
for (let hour = 10; hour < 21; hour += 1) {
  TIMES.push(`${fmt.pad2(hour)}:00`)
  TIMES.push(`${fmt.pad2(hour)}:30`)
}

function stockLabel(status, quantity) {
  if (status === 'OUT') {
    return '本店售罄'
  }
  if (status === 'LOW') {
    return `仅剩 ${quantity} 件`
  }
  return '有货'
}

function emptySheet() {
  return {
    show: false,
    storeId: null,
    storeName: '',
    items: [],
    skuIds: [],
    date: '',
    dateLabel: '',
    minDate: '',
    maxDate: '',
    times: TIMES,
    timeIndex: 0,
    phone: '',
    contactName: '',
    remark: '',
    submitting: false
  }
}

Page({
  data: {
    statusBarHeight: 20,
    loading: true,
    loggedIn: false,
    busy: false,
    groups: [],
    totalChecked: 0,
    sheet: emptySheet()
  },

  onLoad() {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    this.setData({ statusBarHeight: info.statusBarHeight || 20 })
  },

  onShow() {
    const tabBar = typeof this.getTabBar === 'function' && this.getTabBar()
    if (tabBar) {
      tabBar.setData({ selected: 2, transparent: false })
    }
    if (!auth.currentToken()) {
      this.setData({ loggedIn: false, groups: [], totalChecked: 0, loading: false })
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
      const data = await request({ url: '/api/v1/tryon-items', auth: true })
      const groups = data.map(group => {
        const items = group.items.map(item => Object.assign({}, item, {
          checked: true,
          priceText: fmt.formatPrice(item.priceCent),
          stockLabel: stockLabel(item.stockStatus, item.stockQuantity)
        }))
        return Object.assign({}, group, {
          items,
          checkedCount: items.length,
          storeShort: fmt.shortStoreName(group.storeName)
        })
      })
      this.setData({
        loggedIn: true,
        groups,
        totalChecked: groups.reduce((sum, group) => sum + group.checkedCount, 0),
        loading: false
      }, () => this.openPendingBooking())
    } catch (e) {
      this.setData({ loggedIn: Boolean(auth.currentToken()), loading: false })
    }
  },

  openPendingBooking() {
    const app = getApp()
    const storeId = app.globalData.pendingBookStoreId
    if (!storeId) {
      return
    }
    app.globalData.pendingBookStoreId = null
    const index = this.data.groups.findIndex(group => group.storeId === storeId)
    if (index >= 0) {
      this.openSheet(index)
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

  toggleItem(e) {
    const { g, i } = e.currentTarget.dataset
    const groups = this.data.groups
    groups[g].items[i].checked = !groups[g].items[i].checked
    groups[g].checkedCount = groups[g].items.filter(item => item.checked).length
    this.setData({
      groups,
      totalChecked: groups.reduce((sum, group) => sum + group.checkedCount, 0)
    })
  },

  openProduct(e) {
    const { g } = e.currentTarget.dataset
    const item = this.data.groups[g].items[e.currentTarget.dataset.i]
    const group = this.data.groups[g]
    if (!item) {
      return
    }
    wx.navigateTo({ url: `/pages/product/index?id=${item.productId}&storeId=${group.storeId}` })
  },

  removeItem(e) {
    const { g, i } = e.currentTarget.dataset
    const item = this.data.groups[g].items[i]
    if (!item) {
      return
    }
    wx.showActionSheet({
      itemList: ['从清单移除'],
      success: async res => {
        if (res.tapIndex !== 0) {
          return
        }
        try {
          await auth.withAuth(() => request({
            url: `/api/v1/tryon-items/${item.id}`,
            method: 'DELETE',
            auth: true
          }))
          wx.showToast({ title: '已移除', icon: 'none' })
          await this.load()
        } catch (err) {
          // 失败原因由 request 统一提示
        }
      }
    })
  },

  openBookingForGroup(e) {
    this.openSheet(Number(e.currentTarget.dataset.g))
  },

  openSheetFromBar() {
    const groups = this.data.groups
      .map((group, index) => ({ index, count: group.checkedCount }))
      .filter(entry => entry.count > 0)
    if (!groups.length) {
      wx.showToast({ title: '先勾选要试穿的单品', icon: 'none' })
      return
    }
    if (groups.length === 1) {
      this.openSheet(groups[0].index)
      return
    }
    wx.showActionSheet({
      itemList: groups.map(entry => this.data.groups[entry.index].storeShort),
      success: res => this.openSheet(groups[res.tapIndex].index)
    })
  },

  openSheet(index) {
    const group = this.data.groups[index]
    if (!group) {
      return
    }
    const items = group.items.filter(item => item.checked)
    if (!items.length) {
      wx.showToast({ title: '先勾选要试穿的单品', icon: 'none' })
      return
    }
    const user = auth.currentUser()
    const date = fmt.addDaysKey(1)
    const nickname = user && user.nickname && user.nickname !== '微信用户' ? user.nickname : ''
    this.setData({
      sheet: {
        show: true,
        storeId: group.storeId,
        storeName: group.storeName,
        items: items.map(item => ({
          id: item.id,
          skuId: item.skuId,
          title: item.title,
          color: item.color,
          size: item.size,
          coverUrl: item.coverUrl,
          priceText: item.priceText
        })),
        skuIds: items.map(item => item.skuId),
        date,
        dateLabel: fmt.dateText(date),
        minDate: fmt.addDaysKey(0),
        maxDate: fmt.addDaysKey(30),
        times: TIMES,
        timeIndex: 0,
        phone: wx.getStorageSync('xc.phone') || '',
        contactName: nickname,
        remark: '',
        submitting: false
      }
    })
  },

  closeSheet() {
    this.setData({ 'sheet.show': false })
  },

  onDateChange(e) {
    this.setData({
      'sheet.date': e.detail.value,
      'sheet.dateLabel': fmt.dateText(e.detail.value)
    })
  },

  onTimeChange(e) {
    this.setData({ 'sheet.timeIndex': Number(e.detail.value) })
  },

  onPhoneInput(e) {
    this.setData({ 'sheet.phone': e.detail.value })
  },

  onContactInput(e) {
    this.setData({ 'sheet.contactName': e.detail.value })
  },

  onRemarkInput(e) {
    this.setData({ 'sheet.remark': e.detail.value })
  },

  async submitBooking() {
    const sheet = this.data.sheet
    if (sheet.submitting) {
      return
    }
    const phone = (sheet.phone || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    const visitTime = sheet.timeIndex === 0 ? null : `${sheet.times[sheet.timeIndex]}:00`
    this.setData({ 'sheet.submitting': true })
    try {
      await auth.withAuth(() => request({
        url: '/api/v1/appointments',
        method: 'POST',
        auth: true,
        data: {
          storeId: sheet.storeId,
          visitDate: sheet.date,
          visitTime,
          phone,
          contactName: (sheet.contactName || '').trim(),
          remark: (sheet.remark || '').trim(),
          clientRequestId: fmt.requestId(),
          skuIds: sheet.skuIds
        }
      }))
      wx.setStorageSync('xc.phone', phone)
      this.setData({ 'sheet.show': false, 'sheet.submitting': false })
      wx.showToast({ title: '预约已提交', icon: 'success' })
      await this.load()
      setTimeout(() => wx.navigateTo({ url: '/pages/appointments/index' }), 700)
    } catch (e) {
      this.setData({ 'sheet.submitting': false })
    }
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/index' })
  }
})

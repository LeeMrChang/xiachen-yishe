const { formatPrice } = require('../../utils/request')

Component({
  properties: {
    item: {
      type: Object,
      value: null
    },
    cardWidth: {
      type: Number,
      value: 333
    }
  },
  data: {
    imgHeight: 0,
    priceText: '',
    badge: ''
  },
  observers: {
    'item, cardWidth': function (item, cardWidth) {
      if (!item) {
        return
      }
      let imgHeight = 400
      if (item.coverWidth && item.coverHeight) {
        imgHeight = Math.round(cardWidth * item.coverHeight / item.coverWidth)
      }
      let badge = ''
      if (item.stockStatus === 'OUT') {
        badge = '本店售罄'
      } else if (item.stockStatus === 'LOW') {
        badge = '仅剩少量'
      }
      this.setData({
        imgHeight,
        priceText: formatPrice(item.priceCent),
        badge
      })
    }
  },
  methods: {
    onTap() {
      const { item } = this.data
      if (!item) {
        return
      }
      const storeId = getApp().globalData.storeId
      wx.navigateTo({
        url: `/pages/product/index?id=${item.id}${storeId ? `&storeId=${storeId}` : ''}`
      })
    }
  }
})

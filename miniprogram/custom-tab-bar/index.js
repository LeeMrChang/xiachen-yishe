Component({
  properties: {
    transparent: {
      type: Boolean,
      value: false
    }
  },
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页' },
      { pagePath: '/pages/new-arrivals/index', text: '上新' },
      { pagePath: '/pages/tryon-list/index', text: '清单' },
      { pagePath: '/pages/mine/index', text: '我的' }
    ]
  },
  methods: {
    switchTab(e) {
      const { index, path } = e.currentTarget.dataset
      wx.switchTab({ url: path })
      this.setData({ selected: index })
    }
  }
})

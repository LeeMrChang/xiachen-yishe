const auth = require('./utils/auth')

App({
  globalData: {
    appId: 'touristappid',
    // develop 保留本机调试；体验版/正式版使用云端 HTTPS。
    baseUrl: 'http://127.0.0.1:8081',
    token: null,
    user: null,
    storeId: null
  },

  onLaunch() {
    const account = wx.getAccountInfoSync ? wx.getAccountInfoSync() : null
    const mini = account && account.miniProgram
    if (mini && mini.appId) this.globalData.appId = mini.appId
    if (mini && mini.envVersion && mini.envVersion !== 'develop') {
      this.globalData.baseUrl = 'https://xc-api.xiachen.xyz'
    }
    auth.restore(this)
  }
})

const auth = require('./utils/auth')

App({
  globalData: {
    appId: 'touristappid',
    // 开发者工具/模拟器走本机；真机联调改成电脑局域网 IP，上线换成 https 域名
    baseUrl: 'http://127.0.0.1:8081',
    token: null,
    user: null,
    storeId: null
  },

  onLaunch() {
    auth.restore(this)
  }
})

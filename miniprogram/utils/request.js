function request({ url, method = 'GET', data, auth = false }) {
  const app = getApp()
  const header = {
    'Content-Type': 'application/json',
    'X-App-Id': app.globalData.appId
  }
  if (auth && app.globalData.token) {
    header.Authorization = `Bearer ${app.globalData.token}`
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: app.globalData.baseUrl + url,
      method,
      data,
      header,
      timeout: 10000,
      success(res) {
        if (res.statusCode === 200 && res.data && res.data.code === 0) {
          resolve(res.data.data)
          return
        }
        if (res.statusCode === 401 || (res.data && res.data.code === 40101)) {
          // 登录态失效：清掉本地会话，交给 utils/auth 重新登录
          const app = getApp()
          if (app) {
            app.globalData.token = null
            app.globalData.user = null
          }
          wx.removeStorageSync('xc.token')
          wx.removeStorageSync('xc.user')
          reject(res.data || res)
          return
        }
        const message = (res.data && res.data.message) || '请求失败'
        if (res.data && res.data.code) {
          wx.showToast({ title: message, icon: 'none' })
        }
        reject(res.data || res)
      },
      fail(err) {
        wx.showToast({ title: '网络开小差了', icon: 'none' })
        reject(err)
      }
    })
  })
}

function formatPrice(cent) {
  const value = Number(cent || 0) / 100
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

module.exports = { request, formatPrice }

const { request } = require('./request')

const TOKEN_KEY = 'xc.token'
const USER_KEY = 'xc.user'

function currentToken() {
  const app = getApp()
  return (app && app.globalData.token) || wx.getStorageSync(TOKEN_KEY) || ''
}

function currentUser() {
  const app = getApp()
  return (app && app.globalData.user) || wx.getStorageSync(USER_KEY) || null
}

function saveUser(user) {
  const app = getApp()
  if (app) {
    app.globalData.user = user
  }
  wx.setStorageSync(USER_KEY, user)
}

function saveSession(data) {
  const app = getApp()
  if (app) {
    app.globalData.token = data.token
    app.globalData.user = data.user
  }
  wx.setStorageSync(TOKEN_KEY, data.token)
  wx.setStorageSync(USER_KEY, data.user)
}

function clearSession() {
  const app = getApp()
  if (app) {
    app.globalData.token = null
    app.globalData.user = null
  }
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
}

function restore(app) {
  const target = app || getApp()
  if (!target) {
    return
  }
  if (!target.globalData.token) {
    target.globalData.token = wx.getStorageSync(TOKEN_KEY) || null
  }
  if (!target.globalData.user) {
    target.globalData.user = wx.getStorageSync(USER_KEY) || null
  }
}

function wxLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error('wx.login returned no code'))
        }
      },
      fail: reject
    })
  })
}

async function login(extra) {
  const code = await wxLoginCode()
  const data = await request({
    url: '/api/v1/auth/wx-login',
    method: 'POST',
    data: Object.assign({ code }, extra || {})
  })
  saveSession(data)
  return data
}

async function ensureLogin() {
  if (currentToken()) {
    return
  }
  try {
    await login()
  } catch (e) {
    wx.showToast({ title: '登录失败，请重试', icon: 'none' })
    throw e
  }
}

// 先确保已登录，再执行需要登录的操作；token 过期时重新 wx.login 后重试一次
async function withAuth(fn) {
  await ensureLogin()
  try {
    return await fn()
  } catch (e) {
    if (e && e.code === 40101) {
      clearSession()
      await login()
      return await fn()
    }
    throw e
  }
}

async function loadProfile() {
  if (!currentToken()) {
    return null
  }
  try {
    const user = await request({ url: '/api/v1/auth/me', auth: true })
    saveUser(user)
    return user
  } catch (e) {
    return null
  }
}

async function updateProfile(payload) {
  const user = await request({ url: '/api/v1/auth/profile', method: 'PUT', data: payload, auth: true })
  saveUser(user)
  return user
}

async function logout() {
  try {
    await request({ url: '/api/v1/auth/logout', method: 'POST', auth: true })
  } catch (e) {
    // 本地退出即可，不阻塞用户
  }
  clearSession()
}

module.exports = {
  currentToken,
  currentUser,
  restore,
  login,
  ensureLogin,
  withAuth,
  loadProfile,
  updateProfile,
  logout,
  clearSession
}

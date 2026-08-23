const env = require('../config/env')
const storage = require('./storage')
const request = require('./request')

const USER_KEY = 'user_profile'

function getUser() {
  return storage.get(USER_KEY, null)
}

function saveSession(session) {
  if (session && session.accessToken) storage.set('access_token', session.accessToken)
  if (session && session.user) storage.set(USER_KEY, session.user)
  return session && session.user
}

function login() {
  if (env.useMock) {
    const user = getUser() || { id: 'mock-user', nickname: '体验用户', avatarUrl: '', isGuest: true }
    storage.set(USER_KEY, user)
    return Promise.resolve(user)
  }
  // 业务服务端必须用 code 换取自己的 Token，客户端不接触 session_key。
  return new Promise((resolve, reject) => {
    wx.login({
      success: ({ code }) => {
        if (!code) return reject(new Error('未获取到微信登录凭证'))
        request.request({
          url: '/v1/auth/wechat-login',
          method: 'POST',
          data: { code },
          skipAuth: true,
        }).then(saveSession).then(resolve).catch(reject)
      },
      fail: reject,
    })
  })
}

function ensureLogin() {
  const user = getUser()
  return user && request.getToken() ? Promise.resolve(user) : login()
}

function logout() {
  request.clearToken()
  storage.remove(USER_KEY)
}

module.exports = { getUser, login, ensureLogin, logout }

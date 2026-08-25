const storage = require('./storage')
const request = require('./request')

const USER_KEY = 'user_profile'

function getUser() {
  const user = storage.get(USER_KEY, null)
  if (user && user.nickname === '体验用户') return { ...user, nickname: '' }
  return user
}

function saveSession(session) {
  if (session && session.accessToken) storage.set('access_token', session.accessToken)
  if (session && session.user) storage.set(USER_KEY, session.user)
  return session && session.user
}

function login() {
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

// Local development sessions are in-memory and become invalid when the backend restarts.
// Retry the business request once with a fresh login instead of leaving the page at 401.
function withLogin(action) {
  return ensureLogin().then(() => action()).catch((error) => {
    if (!error || error.code !== 401) throw error
    return login().then(() => action())
  })
}

function logout() {
  request.clearToken()
  storage.remove(USER_KEY)
}

module.exports = { getUser, login, ensureLogin, withLogin, logout }

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
  console.log('开始登录...')
  return new Promise((resolve, reject) => {
    wx.login({
      success: ({ code }) => {
        console.log('wx.login 成功，code:', code)
        if (!code) return reject(new Error('未获取到微信登录凭证'))
        request.request({
          url: '/v1/auth/wechat-login',
          method: 'POST',
          data: { code },
          skipAuth: true,
        }).then((session) => {
          console.log('登录请求成功:', session)
          return saveSession(session)
        }).then(resolve).catch((error) => {
          console.error('登录失败:', error)
          reject(error)
        })
      },
      fail: (error) => {
        console.error('wx.login 失败:', error)
        reject(error)
      },
    })
  })
}

function ensureLogin() {
  console.log('ensureLogin 被调用')
  const user = getUser()
  const token = request.getToken()
  console.log('当前用户:', user, '当前token:', token)
  return user && token ? Promise.resolve(user) : login()
}

// Local development sessions are in-memory and become invalid when the backend restarts.
// Retry the business request once with a fresh login instead of leaving the page at 401.
function withLogin(action) {
  console.log('withLogin 被调用')
  return ensureLogin().then(() => {
    console.log('登录完成，开始执行业务请求')
    return action()
  }).catch((error) => {
    console.error('withLogin 捕获错误:', error, '错误码:', error.code)
    if (!error || error.code !== 401) throw error
    console.log('401错误，尝试重新登录')
    return login().then(() => action())
  })
}

function logout() {
  request.clearToken()
  storage.remove(USER_KEY)
}

module.exports = { getUser, login, ensureLogin, withLogin, logout }

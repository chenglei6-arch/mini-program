const request = require('./request')

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
        }).then((session) => {
          request.setToken(session.accessToken)
          resolve(session.user)
        }).catch(reject)
      },
      fail: reject,
    })
  })
}

function ensureLogin() {
  return request.getToken() ? Promise.resolve() : login()
}

// 页面数据加载统一入口：没有会话就先登录；请求失败原样抛给页面提示，不做自动重试。
function withLogin(action) {
  return ensureLogin().then(() => action())
}

module.exports = { login, ensureLogin, withLogin }

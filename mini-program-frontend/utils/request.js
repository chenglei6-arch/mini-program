const env = require('../config/env')

// Token 只放在内存里：小程序重启即重新 wx.login 换取新会话，
// 因此不存在"本地缓存了过期 token"这种需要在 401 时重试兜底的情况。
let accessToken = ''

function getToken() {
  return accessToken
}

function setToken(token) {
  accessToken = token || ''
}

function clearToken() {
  accessToken = ''
}

function buildUrl(path) {
  // 允许服务层传入完整 URL，同时阻止未配置环境时误发请求。
  if (/^https?:\/\//.test(path)) return path
  if (!env.baseUrl) {
    const error = new Error('API base URL is not configured')
    error.code = 'API_NOT_CONFIGURED'
    throw error
  }
  return `${env.baseUrl.replace(/\/$/, '')}/${String(path).replace(/^\//, '')}`
}

// 契约：{ code, message, data, requestId }，code 为 "0" 才是成功；不符合契约直接抛错。
function normalizeResponse(response) {
  const payload = response.data
  if (!payload || typeof payload !== 'object' || !Object.prototype.hasOwnProperty.call(payload, 'code')) {
    const error = new Error('响应格式不符合接口契约')
    error.code = 'INVALID_RESPONSE'
    error.payload = payload
    throw error
  }
  if (payload.code !== '0') {
    const error = new Error(payload.message || '请求失败')
    error.code = payload.code
    error.payload = payload
    throw error
  }
  return payload.data
}

function request(options = {}) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    timeout = env.timeout,
    skipAuth = false,
  } = options

  const requestUrl = buildUrl(url)
  const headers = { 'content-type': 'application/json', ...header }
  if (accessToken && !skipAuth) headers.Authorization = `Bearer ${accessToken}`

  return new Promise((resolve, reject) => {
    wx.request({
      url: requestUrl,
      method,
      data,
      header: headers,
      timeout,
      success: (response) => {
        if (response.statusCode === 401) {
          // 会话已失效：清掉内存 token，页面重新加载时会重新登录。
          clearToken()
          const error = new Error('登录状态已失效')
          error.code = 401
          error.payload = response.data
          reject(error)
          return
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          // 后端统一返回 ApiResponse{code, message, data}，把可读的 message 透传给页面。
          const payload = response.data
          const serverMessage = payload && typeof payload === 'object' ? payload.message : null
          const error = new Error(serverMessage || `请求失败（${response.statusCode}）`)
          error.code = response.statusCode
          error.payload = payload
          reject(error)
          return
        }
        try {
          resolve(normalizeResponse(response))
        } catch (error) {
          reject(error)
        }
      },
      fail: reject,
    })
  })
}

module.exports = { request, getToken, setToken, clearToken }

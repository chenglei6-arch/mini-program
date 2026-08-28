const env = require('../config/env')
const storage = require('./storage')

const TOKEN_KEY = 'access_token'

function getToken() {
  return storage.get(TOKEN_KEY, '')
}

function clearToken() {
  storage.remove(TOKEN_KEY)
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

function normalizeResponse(response) {
  const payload = response.data
  if (!payload || typeof payload !== 'object') return payload
  if (Object.prototype.hasOwnProperty.call(payload, 'code')) {
    if (payload.code !== 0 && payload.code !== 200 && payload.code !== '0') {
      const error = new Error(payload.message || '请求失败')
      error.code = payload.code
      error.payload = payload
      throw error
    }
    return Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload
  }
  return payload
}

function request(options = {}) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    timeout = env.timeout,
    skipAuth = false,
    showError = false,
  } = options

  let requestUrl
  try {
    requestUrl = buildUrl(url)
  } catch (error) {
    if (showError) wx.showToast({ title: '接口环境未配置', icon: 'none' })
    return Promise.reject(error)
  }

  const token = getToken()
  const headers = { 'content-type': 'application/json', ...header }
  if (token && !skipAuth) headers.Authorization = `Bearer ${token}`

  return new Promise((resolve, reject) => {
    wx.request({
      url: requestUrl,
      method,
      data,
      header: headers,
      timeout,
      success: (response) => {
        if (response.statusCode === 401) {
          clearToken()
          const error = new Error('登录状态已失效')
          error.code = 401
          reject(error)
          return
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          const error = new Error(`请求失败（${response.statusCode}）`)
          error.code = response.statusCode
          error.payload = response.data
          reject(error)
          return
        }
        try {
          resolve(normalizeResponse(response))
        } catch (error) {
          reject(error)
        }
      },
      fail: (error) => {
        reject(error)
      },
    })
  }).catch((error) => {
    if (showError) wx.showToast({ title: error.message || '网络异常', icon: 'none' })
    throw error
  })
}

module.exports = { request, getToken, clearToken }

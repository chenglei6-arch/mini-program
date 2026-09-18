const env = require('../config/env')

// assetUrl 是后端给定的相对路径；这里只做域名与版本号拼接，缺配置或缺字段都直接抛错。
function resolveAssetUrl(assetUrl) {
  if (/^https?:\/\//.test(assetUrl)) return assetUrl
  if (!env.baseUrl) {
    const error = new Error('API base URL is not configured')
    error.code = 'API_NOT_CONFIGURED'
    throw error
  }
  if (!assetUrl) throw new Error('缺少 assetUrl，无法拼接素材地址')
  const url = `${env.baseUrl.replace(/\/$/, '')}/${String(assetUrl).replace(/^\//, '')}`
  if (!env.assetVersion) return url
  return `${url}${url.includes('?') ? '&' : '?'}v=${encodeURIComponent(env.assetVersion)}`
}

module.exports = { resolveAssetUrl }

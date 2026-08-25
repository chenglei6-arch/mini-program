const env = require('../config/env')

function resolveAssetUrl(assetUrl) {
  if (!assetUrl || /^https?:\/\//.test(assetUrl)) return assetUrl || ''
  if (!env.baseUrl) return assetUrl
  const url = `${env.baseUrl.replace(/\/$/, '')}/${String(assetUrl).replace(/^\//, '')}`
  const version = env.assetVersion ? `v=${encodeURIComponent(env.assetVersion)}` : ''
  return version ? `${url}${url.includes('?') ? '&' : '?'}${version}` : url
}

module.exports = { resolveAssetUrl }

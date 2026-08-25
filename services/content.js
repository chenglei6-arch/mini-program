const request = require('../utils/request')
const { resolveAssetUrl } = require('../utils/assets')

function getFrogs() {
  return request.request({ url: '/v1/content/frogs' }).then((data) => ({
    ...data,
    items: Array.isArray(data.items)
      ? data.items.map((frog) => ({ ...frog, assetUrl: resolveAssetUrl(frog.assetUrl) }))
      : [],
  }))
}

function getFrogDetail(id) {
  return request.request({ url: `/v1/content/frogs/${encodeURIComponent(id)}` }).then((frog) => ({
    ...frog,
    assetUrl: resolveAssetUrl(frog.assetUrl),
  }))
}

module.exports = { getFrogs, getFrogDetail }

const request = require('../utils/request')
const { resolveAssetUrl } = require('../utils/assets')

function getHomeSummary() {
  return request.request({ url: '/v1/home/summary' }).then((summary) => ({
    ...summary,
    frogs: summary.frogs.map((frog) => ({ ...frog, assetUrl: resolveAssetUrl(frog.assetUrl) })),
  }))
}

function getWelfareSummary() {
  return request.request({ url: '/v1/welfare/summary' })
}

module.exports = { getHomeSummary, getWelfareSummary }

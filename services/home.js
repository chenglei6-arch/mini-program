const request = require('../utils/request')
const { resolveAssetUrl } = require('../utils/assets')

function getHomeSummary() {
  return request.request({ url: '/v1/home/summary' }).then((summary) => ({
    ...summary,
    status: 'DEVELOPMENT',
    fundAmount: null,
    fundUpdateAt: null,
    activity: [],
    frogs: Array.isArray(summary.frogs)
      ? summary.frogs.map((frog) => ({ ...frog, assetUrl: resolveAssetUrl(frog.assetUrl) }))
      : [],
  }))
}

function getWelfareSummary() {
  return request.request({ url: '/v1/welfare/summary' }).then((summary) => ({
    ...summary,
    status: 'DEVELOPMENT',
    fundAmount: null,
    updatedAt: null,
    reports: [],
  }))
}

module.exports = { getHomeSummary, getWelfareSummary }

const request = require('../utils/request')
const { resolveAssetUrl } = require('../utils/assets')

function getHomeSummary() {
  return request.request({ url: '/v1/home/summary' }).then((summary) => ({
    ...summary,
    // Do not expose seeded test cards if the connected backend has not yet picked up
    // CONTENT_INCLUDE_TEST_DATA=false.
    games: Array.isArray(summary.games)
      ? summary.games.filter((game) => game && !String(game.id || '').startsWith('test-'))
      : [],
    fundAmount: summary.fundAmount || null,
    fundUpdateAt: summary.fundUpdateAt || null,
    activity: Array.isArray(summary.activity) ? summary.activity : [],
    frogs: Array.isArray(summary.frogs)
      ? summary.frogs.map((frog) => ({ ...frog, assetUrl: resolveAssetUrl(frog.assetUrl) }))
      : [],
  }))
}

function getWelfareSummary() {
  return request.request({ url: '/v1/welfare/summary' }).then((summary) => ({
    ...summary,
    fundAmount: summary.fundAmount || null,
    updatedAt: summary.updatedAt || null,
    reports: Array.isArray(summary.reports) ? summary.reports : [],
  }))
}

module.exports = { getHomeSummary, getWelfareSummary }

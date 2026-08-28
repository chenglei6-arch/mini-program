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

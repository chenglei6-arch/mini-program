const request = require('../utils/request')
const { resolveAssetUrl } = require('../utils/assets')

function getGameProgress(gameId) {
  return request.request({ url: `/v1/games/${gameId}/progress` })
}

function submitGameEvent(gameId, event, idempotencyKey) {
  const options = { url: `/v1/games/${gameId}/events`, method: 'POST', data: event }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

function unlockByCode(code) {
  return request.request({ url: '/v1/unlocks/redeem', method: 'POST', data: { code } }).then((result) => ({
    ...result,
    character: { ...result.character, assetUrl: resolveAssetUrl(result.character.assetUrl) },
  }))
}

// 青蛙拼图相关API
function getFrogComponents(frogId) {
  return request.request({ url: `/v1/games/frog-puzzle/frogs/${encodeURIComponent(frogId)}/components` })
}

function submitFrogComplete(frogId, payload, idempotencyKey) {
  const event = {
    type: 'frog_completed',
    payload: {
      frogId,
      ...payload
    }
  }
  const options = { url: '/v1/games/frog-puzzle/events', method: 'POST', data: event }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

module.exports = {
  getGameProgress,
  submitGameEvent,
  unlockByCode,
  getFrogComponents,
  submitFrogComplete
}

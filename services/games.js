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
    character: result.character && typeof result.character === 'object'
      ? { ...result.character, assetUrl: resolveAssetUrl(result.character.assetUrl) }
      : null,
  }))
}

// 青蛙拼图相关API
function getFrogComponents(frogId) {
  return request.request({ url: `/v1/games/frog-puzzle/frogs/${encodeURIComponent(frogId)}/components` })
}

function getUserComponents() {
  return request.request({ url: '/v1/games/frog-puzzle/user/components' })
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

function getPuzzleProgress() {
  return request.request({ url: '/v1/games/frog-puzzle/progress' })
}

module.exports = {
  getGameProgress,
  submitGameEvent,
  unlockByCode,
  getFrogComponents,
  getUserComponents,
  submitFrogComplete,
  getPuzzleProgress
}

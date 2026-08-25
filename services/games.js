const request = require('../utils/request')

function getGameProgress(gameId) {
  return request.request({ url: `/v1/games/${gameId}/progress` })
}

function submitGameEvent(gameId, event, idempotencyKey) {
  const options = { url: `/v1/games/${gameId}/events`, method: 'POST', data: event }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

function unlockByCode(code) {
  return request.request({ url: '/v1/unlocks/redeem', method: 'POST', data: { code } }).then(() => ({
    status: 'DEVELOPMENT',
    message: '扫码解锁功能开发中',
  }))
}

module.exports = { getGameProgress, submitGameEvent, unlockByCode }

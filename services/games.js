const env = require('../config/env')
const request = require('../utils/request')

function getGameProgress(gameId) {
  if (gameId === 'story' || !env.useMock) return request.request({ url: `/v1/games/${gameId}/progress` })
  return Promise.resolve({ gameId, completed: 0, total: 1 })
}

function submitGameEvent(gameId, event, idempotencyKey) {
  if (env.useMock && gameId !== 'story') {
    return Promise.resolve({ accepted: true, gameId, event })
  }
  const options = { url: `/v1/games/${gameId}/events`, method: 'POST', data: event }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

function unlockByCode(code) {
  return env.useMock
    ? Promise.resolve({ accepted: true, code, characterName: '待解锁角色', message: '演示环境已接收二维码' })
    : request.request({ url: '/v1/unlocks/redeem', method: 'POST', data: { code } })
}

module.exports = { getGameProgress, submitGameEvent, unlockByCode }

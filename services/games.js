const env = require('../config/env')
const request = require('../utils/request')

function getGameProgress(gameId) {
  return env.useMock ? Promise.resolve({ gameId, completed: 0, total: gameId === 'story' ? 5 : 1 }) : request.request({ url: `/v1/games/${gameId}/progress` })
}

function submitGameEvent(gameId, event) {
  return env.useMock ? Promise.resolve({ accepted: true, gameId, event }) : request.request({ url: `/v1/games/${gameId}/events`, method: 'POST', data: event })
}

function unlockByCode(code) {
  return env.useMock ? Promise.resolve({ accepted: true, code, characterName: '待解锁角色', message: '演示环境已接收二维码' }) : request.request({ url: '/v1/unlocks/redeem', method: 'POST', data: { code } })
}

module.exports = { getGameProgress, submitGameEvent, unlockByCode }

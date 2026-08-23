const env = require('../config/env')
const request = require('../utils/request')

function getGameProgress(gameId) {
  return env.useMock ? Promise.resolve(getMockProgress(gameId)) : request.request({ url: `/v1/games/${gameId}/progress` })
}

function submitGameEvent(gameId, event, idempotencyKey) {
  if (env.useMock) {
    if (gameId === 'story') return Promise.resolve(advanceMockStory(event))
    return Promise.resolve({ accepted: true, gameId, event })
  }
  const options = { url: `/v1/games/${gameId}/events`, method: 'POST', data: event }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

function unlockByCode(code) {
  return env.useMock ? Promise.resolve({ accepted: true, code, characterName: '待解锁角色', message: '演示环境已接收二维码' }) : request.request({ url: '/v1/unlocks/redeem', method: 'POST', data: { code } })
}

module.exports = { getGameProgress, submitGameEvent, unlockByCode }

let mockStory = createMockStory()

function getMockProgress(gameId) {
  if (gameId === 'story') return mockStory
  return { gameId, completed: 0, total: 1 }
}

function createMockStory() {
  return buildMockStoryResponse('intro', '开场 · 萨满古洞', '穿过缠绕盘结的老藤山隘，你来到萨满古洞。传话灵蛙告诉你，三份灵纹古卷必须留在故土，而副本可以走向人间。', '传话灵蛙', [
    mockChoice('A', '红松松岗 · 《山林规约卷》', '追寻《山林规约卷》'),
    mockChoice('B', '鸭绿江湿地 · 《水泽灵物卷》', '追寻《水泽灵物卷》'),
    mockChoice('C', '天池冰渊 · 《冰雪传说卷》', '追寻《冰雪传说卷》'),
  ])
}

function advanceMockStory(event) {
  if (!event || event.type === 'reset') {
    mockStory = createMockStory()
    return mockStory
  }
  const choiceId = event.payload && event.payload.choiceId
  if (!choiceId) return mockStory
  const state = mockStory.state
  const route = state.sceneId === 'intro' || state.sceneId === 'hub' ? choiceId : (state.currentRoute || choiceId[0])
  if (state.sceneId === 'intro' || state.sceneId === 'hub') {
    state.currentRoute = choiceId
    state.sceneId = 'guardian'
    state.sceneTitle = `${choiceId}路线 · 守卷灵蛙`
    state.sceneText = '你在古卷前停下脚步。请决定如何对待留在故土的传承。'
    state.speaker = '传话灵蛙'
    state.choices = [mockChoice(`${choiceId}1`, '取走实体古卷', '带走本体'), mockChoice(`${choiceId}2`, '留在原地抄录副本', '守住故土')]
  } else if (state.sceneId === 'guardian') {
    state.sceneId = 'propagation'
    state.sceneTitle = '古卷的传播方式'
    state.sceneText = '实体与副本的命运已经分开。现在请决定如何让后人接近乌勒本。'
    state.speaker = '灵蛙'
    state.choices = [mockChoice(`${route}a`, '恪守原文，不作通俗改写', '保持原貌'), mockChoice(`${route}b`, '保留内核，允许适度改写', '走向人间')]
  } else if (state.sceneId === 'propagation') {
    state.completedRoutes = state.completedRoutes.concat(route)
    if (state.completedRoutes.length === 3) {
      state.sceneId = 'finale'
      state.sceneTitle = '终局 · 古卷的命运'
      state.sceneText = '三卷全部探索完毕。现在，古卷的命运由你定夺。'
      state.choices = [mockChoice('final-1', '山林归藏', '实体归还故土'), mockChoice('final-2', '人间活化', '副本走向人间'), mockChoice('final-3', '私藏束之', '全部带出古洞')]
    } else {
      state.sceneId = 'hub'
      state.sceneTitle = '萨满古洞 · 路线回望'
      state.sceneText = '篝火等待着下一卷古卷的回声。请选择尚未探索的地域。'
      state.choices = ['A', 'B', 'C'].filter((item) => !state.completedRoutes.includes(item)).map((item) => mockChoice(item, `进入 ${item} 路线`, '继续探索'))
    }
  } else if (state.sceneId === 'finale') {
    state.sceneId = 'ending'
    state.finished = true
    state.sceneTitle = '结局 2 · 纸韵新鸣 · 现世活化'
    state.sceneText = '你守住古卷本体，也让故事借新时代媒介获得新生。'
    state.ending = { id: '2', title: '纸韵新鸣 · 现世活化', text: state.sceneText, label: '演示结局' }
    state.choices = []
  }
  mockStory = buildMockStoryResponse(state.sceneId, state.sceneTitle, state.sceneText, state.speaker, state.choices, state)
  return mockStory
}

function buildMockStoryResponse(sceneId, sceneTitle, sceneText, speaker, choices, previousState) {
  const state = previousState || {
    sceneId, sceneTitle, sceneText, speaker, choices,
    currentRoute: null, completedRoutes: [], routeOrder: [], routeRecords: {}, routePropagation: {},
    inventory: [], notes: [], songs: [], temporaryStates: [], scores: { reverence: 0, openness: 0, desire: 0, soulRelief: 0 },
    hiddenWish: null, ending: null, finished: false, gameOver: false,
  }
  state.sceneId = sceneId
  state.sceneTitle = sceneTitle
  state.sceneText = sceneText
  state.speaker = speaker
  state.choices = choices
  return { gameId: 'story', completed: state.completedRoutes.length, total: 3, finished: state.finished, gameOver: state.gameOver, version: 1, updatedAt: new Date().toISOString(), state }
}

function mockChoice(id, label, hint) {
  return { id, label, hint }
}

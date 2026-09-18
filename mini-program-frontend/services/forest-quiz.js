const request = require('../utils/request')

function getProgress() {
  return request.request({ url: '/v1/games/forest-quiz/progress' })
}

function submitAnswer(levelId, questionId, optionId, idempotencyKey) {
  const options = {
    url: '/v1/games/forest-quiz/events',
    method: 'POST',
    data: { type: 'quiz_answer', payload: { levelId, questionId, optionId } },
  }
  if (idempotencyKey) options.header = { 'Idempotency-Key': idempotencyKey }
  return request.request(options)
}

module.exports = { getProgress, submitAnswer }

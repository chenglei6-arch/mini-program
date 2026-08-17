const env = require('../config/env')
const request = require('../utils/request')
const mock = require('./mock-data')

function getHomeSummary() {
  return env.useMock ? Promise.resolve(mock.home()) : request.request({ url: '/v1/home/summary' })
}

function getWelfareSummary() {
  return env.useMock ? Promise.resolve({ fundAmount: '12,480.00', reports: [], updatedAt: '2026-08-17' }) : request.request({ url: '/v1/welfare/summary' })
}

module.exports = { getHomeSummary, getWelfareSummary }

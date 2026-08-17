const env = require('../config/env')
const request = require('../utils/request')
const mock = require('./mock-data')

function getProfile() {
  return env.useMock ? Promise.resolve(mock.profile()) : request.request({ url: '/v1/me/profile' })
}

function getRankings(type = 'total') {
  const data = { type, updatedAt: '2026-08-17 12:00', items: [], myRank: null }
  return env.useMock ? Promise.resolve(data) : request.request({ url: '/v1/rankings', data: { type } })
}

function updateProfile(profile) {
  if (env.useMock) return Promise.resolve(profile)
  return request.request({ url: '/v1/me/profile', method: 'PATCH', data: profile })
}

module.exports = { getProfile, getRankings, updateProfile }

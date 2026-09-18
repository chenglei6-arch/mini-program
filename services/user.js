const request = require('../utils/request')

function getProfile() {
  return request.request({ url: '/v1/me/profile' })
}

function getRankings(type = 'total') {
  return request.request({ url: '/v1/rankings', data: { type } })
}

function updateProfile(profile) {
  return request.request({ url: '/v1/me/profile', method: 'PATCH', data: profile })
}

module.exports = { getProfile, getRankings, updateProfile }

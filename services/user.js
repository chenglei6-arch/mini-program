const request = require('../utils/request')

function getProfile() {
  return request.request({ url: '/v1/me/profile' }).then((profile) => ({
    ...profile,
    status: 'DEVELOPMENT',
    stats: { status: 'DEVELOPMENT' },
    badges: [],
    orders: [],
  }))
}

function getRankings(type = 'total') {
  return request.request({ url: '/v1/rankings', data: { type } }).then((data) => ({
    ...data,
    status: 'DEVELOPMENT',
    items: [],
    updatedAt: null,
    myRank: null,
  }))
}

function updateProfile(profile) {
  return request.request({ url: '/v1/me/profile', method: 'PATCH', data: profile })
}

module.exports = { getProfile, getRankings, updateProfile }

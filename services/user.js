const request = require('../utils/request')

function getProfile() {
  return request.request({ url: '/v1/me/profile' }).then((profile) => ({
    ...profile,
    stats: profile.stats || {},
    badges: Array.isArray(profile.badges) ? profile.badges : [],
    // 订单依赖支付能力，后端暂返回空数组，页面据此显示占位文案。
    orders: Array.isArray(profile.orders) ? profile.orders : [],
  }))
}

function getRankings(type = 'total') {
  return request.request({ url: '/v1/rankings', data: { type } }).then((data) => ({
    ...data,
    items: Array.isArray(data.items) ? data.items : [],
    myRank: data.myRank === undefined ? null : data.myRank,
  }))
}

function updateProfile(profile) {
  return request.request({ url: '/v1/me/profile', method: 'PATCH', data: profile })
}

module.exports = { getProfile, getRankings, updateProfile }

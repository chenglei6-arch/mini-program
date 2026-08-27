const environments = {
  development: {
    name: 'development',
    baseUrl: 'https://www.luolikongchenglei.asia/',
    assetVersion: '3',
    timeout: 10000,
  },
  staging: {
    name: 'staging',
    baseUrl: '',
    assetVersion: '3',
    timeout: 10000,
  },
  production: {
    name: 'production',
    baseUrl: '',
    assetVersion: '3',
    timeout: 10000,
  },
}

const current = 'development'

module.exports = environments[current]

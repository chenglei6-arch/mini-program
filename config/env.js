const environments = {
  development: {
    name: 'development',
    baseUrl: 'http://192.168.1.3:8080',
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

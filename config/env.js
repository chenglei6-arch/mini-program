const environments = {
  development: {
    name: 'development',
    // baseUrl: 'https://www.luolikongchenglei.asia/',
    baseUrl: 'http://localhost:8080',
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

const config = environments[current]
console.log('=== 环境配置加载 ===')
console.log('当前环境:', current)
console.log('baseUrl:', config.baseUrl)
console.log('完整配置:', config)

module.exports = config

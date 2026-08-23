const environments = {
  development: {
    name: 'development',
    baseUrl: 'http://localhost:8080',
    useMock: false,
    timeout: 10000,
  },
  staging: {
    name: 'staging',
    baseUrl: '',
    useMock: false,
    timeout: 10000,
  },
  production: {
    name: 'production',
    baseUrl: '',
    useMock: false,
    timeout: 10000,
  },
}

// Change this value only when the matching backend contract and domain whitelist are ready.
const current = 'development'

module.exports = environments[current]

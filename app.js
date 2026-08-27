const auth = require('./utils/auth')

App({
  onLaunch() {
    console.log('=== 小程序启动 ===')
    this.globalData.launchAt = Date.now()
    console.log('启动时间:', this.globalData.launchAt)
  },
  onError(error) {
    console.error('=== 小程序全局错误 ===', error)
  },
  globalData: {
    userInfo: null
  },
  ensureLogin() {
    return auth.ensureLogin().then((user) => {
      this.globalData.userInfo = user
      return user
    })
  },
})

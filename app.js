const auth = require('./utils/auth')

App({
  onLaunch() {
    this.globalData.launchAt = Date.now()
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

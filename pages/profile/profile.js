const auth = require('../../utils/auth')
const storage = require('../../utils/storage')
const userService = require('../../services/user')

Component({
  data: {
    loading: true,
    profile: null,
    nicknameDraft: '',
  },
  lifetimes: {
    attached() {
      auth.withLogin(() => this.loadProfile()).catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: error && error.code === 401 ? '登录失败，请稍后重试' : '个人信息加载失败', icon: 'none' })
      })
    },
  },
  methods: {
    loadProfile() {
      return userService.getProfile()
        .then((profile) => {
          const local = auth.getUser()
          if (local) profile.user = { ...profile.user, ...local }
          this.setData({ profile, nicknameDraft: profile.user.nickname || '' })
        })
        .finally(() => this.setData({ loading: false }))
    },
    chooseAvatar(event) {
      // 取消选择时，开发者工具可能触发空详情；不要把空地址写入资料或提交接口。
      const avatarUrl = event && event.detail && event.detail.avatarUrl
      if (!avatarUrl) return
      this.setData({ 'profile.user.avatarUrl': avatarUrl })
      this.saveProfile({ avatarUrl })
    },
    changeNickname(event) {
      const nickname = event.detail.value.trim()
      if (!nickname) return
      this.setData({ 'profile.user.nickname': nickname, nicknameDraft: nickname })
      this.saveProfile({ nickname })
    },
    saveProfile(patch) {
      const user = { ...this.data.profile.user, ...patch, isGuest: false }
      auth.withLogin(() => userService.updateProfile(patch))
        .then(() => storage.set('user_profile', user))
        .catch(() => wx.showToast({ title: '资料保存失败', icon: 'none' }))
    },
    openRankings() { wx.navigateTo({ url: '/pages/rankings/rankings' }) },
    openWelfare() { wx.navigateTo({ url: '/pages/welfare/welfare' }) },
    scanUnlock() { wx.navigateTo({ url: '/pages/scan/scan' }) },
  },
})

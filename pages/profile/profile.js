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
      auth.ensureLogin().then(() => this.loadProfile()).catch(() => {
        this.setData({ loading: false })
        wx.showToast({ title: '登录失败，请稍后重试', icon: 'none' })
      })
    },
  },
  methods: {
    loadProfile() {
      userService.getProfile()
        .then((profile) => {
          const local = auth.getUser()
          if (local) profile.user = { ...profile.user, ...local }
          this.setData({ profile, nicknameDraft: profile.user.nickname || '' })
        })
        .catch(() => wx.showToast({ title: '个人信息加载失败', icon: 'none' }))
        .finally(() => this.setData({ loading: false }))
    },
    chooseAvatar(event) {
      const avatarUrl = event.detail.avatarUrl
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
      userService.updateProfile(patch).then(() => storage.set('user_profile', user))
    },
    openRankings() { wx.navigateTo({ url: '/pages/rankings/rankings' }) },
    openWelfare() { wx.navigateTo({ url: '/pages/welfare/welfare' }) },
    scanUnlock() { wx.navigateTo({ url: '/pages/scan/scan' }) },
  },
})

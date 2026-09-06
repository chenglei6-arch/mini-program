const auth = require('../../../utils/auth')
const gameService = require('../../../services/games')

Component({
  data: {
    loading: true,
    submitting: false,
    resetting: false,
    routeIds: ['A', 'B', 'C'],
    progress: null,
    state: null,
    // 打字机效果：逐段显示场景正文，轻触正文可跳过
    revealed: 0,
    typing: false,
  },

  lifetimes: {
    attached() {
      auth.withLogin(() => this.loadStory()).catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: error && error.code === 401 ? '登录失败，请稍后重试' : '故事进度加载失败', icon: 'none' })
      })
    },
    detached() {
      this.clearTypewriter()
    },
  },

  methods: {
    loadStory() {
      this.setData({ loading: true })
      return gameService.getGameProgress('story')
        .then((progress) => this.setStory(progress))
        .finally(() => this.setData({ loading: false }))
    },

    setStory(progress) {
      this.clearTypewriter()
      const state = progress && progress.state ? progress.state : null
      const total = state && Array.isArray(state.sceneParagraphs) ? state.sceneParagraphs.length : 0
      if (!total) {
        this.setData({ progress, state, revealed: 0, typing: false })
        return
      }
      this.setData({ progress, state, revealed: 1, typing: total > 1 })
      if (total > 1) {
        this._typeTimer = setInterval(() => {
          const next = this.data.revealed + 1
          if (next >= total) {
            this.clearTypewriter()
            this.setData({ revealed: total, typing: false })
          } else {
            this.setData({ revealed: next })
          }
        }, 450)
      }
    },

    clearTypewriter() {
      if (this._typeTimer) {
        clearInterval(this._typeTimer)
        this._typeTimer = null
      }
    },

    revealAll() {
      if (!this.data.typing) return
      this.clearTypewriter()
      const total = this.data.state && Array.isArray(this.data.state.sceneParagraphs)
        ? this.data.state.sceneParagraphs.length : 0
      this.setData({ revealed: total, typing: false })
    },

    choose(event) {
      const choiceId = event.currentTarget.dataset.choiceId
      if (!choiceId || this.data.submitting || !this.data.state || this.data.state.finished || this.data.state.gameOver) return
      this.setData({ submitting: true })
      const idempotencyKey = `story-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      gameService.submitGameEvent('story', {
        type: 'story_choice',
        payload: { choiceId },
      }, idempotencyKey)
        .then((progress) => {
          this.setStory(progress)
          if (progress.finished || progress.gameOver) wx.showToast({ title: '故事结局已揭晓', icon: 'success' })
        })
        .catch((error) => wx.showToast({ title: error.message || '选择提交失败', icon: 'none' }))
        .finally(() => this.setData({ submitting: false }))
    },

    resetStory() {
      if (this.data.resetting || this.data.submitting) return
      wx.showModal({
        title: '重新开始故事',
        content: '当前路线与结局将被清空，确定重新开始吗？',
        success: (result) => {
          if (!result.confirm) return
          this.setData({ resetting: true })
          gameService.submitGameEvent('story', { type: 'reset' })
            .then((progress) => this.setStory(progress))
            .catch(() => wx.showToast({ title: '故事重置失败', icon: 'none' }))
            .finally(() => this.setData({ resetting: false }))
        },
      })
    },

    onShareAppMessage() {
      return { title: '我正在探索林蛙谷的古卷传说', path: '/pages/games/story/story' }
    },
  },
})

const gamesService = require('../../../services/games')
const env = require('../../../config/env')

// 处理图片URL：如果是相对路径，拼接baseUrl
function buildImageUrl(assetUrl) {
  if (!assetUrl) return ''
  if (/^https?:\/\//.test(assetUrl)) return assetUrl // 已经是完整URL
  if (assetUrl.startsWith('/')) {
    return `${env.baseUrl.replace(/\/$/, '')}${assetUrl}` // 拼接baseUrl
  }
  return assetUrl
}

Component({
  data: {
    loading: true,
    error: null,

    // 游戏进度数据
    gameId: 'guardian',
    completed: 0,
    total: 9,
    finished: false,

    // 每日状态
    dailyFreeDraws: 3,
    drawsUsed: 0,
    remainingDraws: 0,
    shareBonusClaimed: false,
    canClaimShareBonus: true,

    // 收集状态
    collectedFrogIds: [],
    collection: [],
    badges: [],

    // 当前挑战
    activeChallenge: null,
    selectedPattern: null,
    submitting: false,

    // 动画状态
    shaking: false,
    showResult: false,
    resultType: null, // 'success' | 'error' | 'collected'
    resultMessage: '',
    guardianCard: null,
    newlyUnlockedBadges: [],
  },

  lifetimes: {
    attached() {
      this.loadProgress()
    },
  },

  methods: {
    // 加载游戏进度
    async loadProgress() {
      try {
        this.setData({ loading: true, error: null })
        const progress = await gamesService.getGameProgress(this.data.gameId)

        const state = progress.state || {}
        this.setData({
          loading: false,
          completed: progress.completed || 0,
          total: progress.total || 9,
          finished: progress.finished || false,

          dailyFreeDraws: state.dailyFreeDraws || 3,
          drawsUsed: state.drawsUsed || 0,
          remainingDraws: state.remainingDraws || 0,
          shareBonusClaimed: state.shareBonusClaimed || false,
          canClaimShareBonus: state.canClaimShareBonus !== false,

          collectedFrogIds: state.collectedFrogIds || [],
          collection: (state.collection || []).map(item => ({
            ...item,
            assetUrl: buildImageUrl(item.assetUrl)
          })),
          badges: state.badges || [],

          activeChallenge: state.activeChallenge ? {
            ...state.activeChallenge,
            assetUrl: buildImageUrl(state.activeChallenge.assetUrl)
          } : null,
          selectedPattern: null,
        })
      } catch (error) {
        console.error('加载守护神进度失败:', error)
        this.setData({
          loading: false,
          error: error.message || '加载失败，请重试'
        })
      }
    },

    // 摇一摇抽取守护神
    async onShake() {
      if (this.data.shaking || this.data.submitting) return

      const { remainingDraws, activeChallenge } = this.data

      if (activeChallenge) {
        wx.showToast({ title: '请先完成当前守护神纹样题', icon: 'none' })
        return
      }

      if (remainingDraws <= 0) {
        wx.showToast({
          title: '今日摇一摇次数已用完，分享后可额外获得 1 次',
          icon: 'none',
          duration: 2500
        })
        return
      }

      try {
        this.setData({ shaking: true })

        // 触发摇一摇动画
        wx.vibrateShort({ type: 'medium' })

        const idempotencyKey = `draw-${Date.now()}-${Math.random().toString(36).slice(2)}`
        const result = await gamesService.submitGameEvent(
          this.data.gameId,
          { type: 'draw' },
          idempotencyKey
        )

        await this.handleDrawResult(result)
      } catch (error) {
        console.error('摇一摇失败:', error)
        wx.showToast({
          title: error.message || '摇一摇失败，请重试',
          icon: 'none'
        })
      } finally {
        this.setData({ shaking: false })
      }
    },

    // 处理抽取结果
    async handleDrawResult(result) {
      const progress = result.progress || {}
      const action = result.action || {}
      const state = progress.state || {}

      // 处理activeChallenge的图片URL
      const activeChallenge = state.activeChallenge
      if (activeChallenge) {
        activeChallenge.assetUrl = buildImageUrl(activeChallenge.assetUrl)
      }

      this.setData({
        completed: progress.completed || 0,
        drawsUsed: state.drawsUsed || 0,
        remainingDraws: state.remainingDraws || 0,
        activeChallenge: activeChallenge || null,
        selectedPattern: null,
      })

      if (action.drawn) {
        // 成功抽到守护神，等待用户答题
        wx.showToast({
          title: action.message || '守护神已现身',
          icon: 'success',
          duration: 1500
        })
      } else {
        wx.showToast({
          title: action.message || '请先完成当前守护神纹样题',
          icon: 'none'
        })
      }
    },

    // 选择纹样选项
    onSelectPattern(e) {
      const pattern = e.currentTarget.dataset.pattern
      this.setData({ selectedPattern: pattern })
    },

    // 提交答案
    async onSubmitAnswer() {
      const { activeChallenge, selectedPattern, submitting } = this.data

      if (submitting) return
      if (!activeChallenge) return
      if (!selectedPattern) {
        wx.showToast({ title: '请选择一个纹样', icon: 'none' })
        return
      }

      try {
        this.setData({ submitting: true })

        const idempotencyKey = `answer-${activeChallenge.roundId}-${Date.now()}`
        const result = await gamesService.submitGameEvent(
          this.data.gameId,
          {
            type: 'answer',
            payload: {
              roundId: activeChallenge.roundId,
              pattern: selectedPattern
            }
          },
          idempotencyKey
        )

        await this.handleAnswerResult(result)
      } catch (error) {
        console.error('提交答案失败:', error)
        wx.showToast({
          title: error.message || '提交失败，请重试',
          icon: 'none'
        })
      } finally {
        this.setData({ submitting: false })
      }
    },

    // 处理答题结果
    async handleAnswerResult(result) {
      const progress = result.progress || {}
      const action = result.action || {}
      const state = progress.state || {}

      if (action.correct) {
        // 答对了，处理guardianCard的图片URL
        const guardianCard = action.guardianCard
        if (guardianCard) {
          guardianCard.assetUrl = buildImageUrl(guardianCard.assetUrl)
        }

        this.setData({
          completed: progress.completed || 0,
          finished: progress.finished || false,
          collectedFrogIds: state.collectedFrogIds || [],
          collection: (state.collection || []).map(item => ({
            ...item,
            assetUrl: buildImageUrl(item.assetUrl)
          })),
          badges: state.badges || [],
          activeChallenge: null,
          selectedPattern: null,

          showResult: true,
          resultType: 'success',
          resultMessage: action.message || '守护成功',
          guardianCard: guardianCard || null,
          newlyUnlockedBadges: action.newlyUnlockedBadges || [],
        })

        wx.vibrateShort({ type: 'heavy' })

        // 3秒后自动关闭结果弹窗
        setTimeout(() => {
          this.setData({ showResult: false })
        }, 3000)
      } else {
        // 答错了
        wx.showToast({
          title: action.message || '纹样不对，再试一次',
          icon: 'none'
        })
        this.setData({ selectedPattern: null })
      }
    },

    // 关闭结果弹窗
    onCloseResult() {
      this.setData({ showResult: false })
    },

    // 领取分享奖励（分享成功后自动调用）
    async claimShareBonus() {
      const { canClaimShareBonus, shareBonusClaimed, submitting } = this.data

      if (submitting) return
      if (shareBonusClaimed || !canClaimShareBonus) {
        return
      }

      try {
        this.setData({ submitting: true })

        const idempotencyKey = `share-${Date.now()}-${Math.random().toString(36).slice(2)}`
        const result = await gamesService.submitGameEvent(
          this.data.gameId,
          { type: 'share' },
          idempotencyKey
        )

        const progress = result.progress || {}
        const action = result.action || {}
        const state = progress.state || {}

        this.setData({
          shareBonusClaimed: state.shareBonusClaimed || false,
          canClaimShareBonus: state.canClaimShareBonus !== false,
          remainingDraws: state.remainingDraws || 0,
        })

        wx.showToast({
          title: action.message || '分享成功，已获得 1 次额外机会',
          icon: 'success'
        })
      } catch (error) {
        console.error('领取分享奖励失败:', error)
        wx.showToast({
          title: error.message || '领取失败',
          icon: 'none'
        })
      } finally {
        this.setData({ submitting: false })
      }
    },

    // 重试加载
    onRetry() {
      this.loadProgress()
    },
  },

  // 页面级分享配置
  onShareAppMessage() {
    // 分享成功后自动领取奖励
    this.claimShareBonus()

    return {
      title: '林蛙守护神 - 摇一摇解锁守护神',
      path: '/pages/games/guardian/guardian',
      imageUrl: '/assets/share-guardian.png'
    }
  },

  onShareTimeline() {
    // 分享到朋友圈
    this.claimShareBonus()

    return {
      title: '林蛙守护神 - 摇一摇解锁守护神',
      imageUrl: '/assets/share-guardian.png'
    }
  },
})


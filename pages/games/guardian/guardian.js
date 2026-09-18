const gamesService = require('../../../services/games')
const auth = require('../../../utils/auth')
const { resolveAssetUrl } = require('../../../utils/assets')

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
      this.startShakeListener()
    },
    detached() {
      this.stopShakeListener()
      if (this._resultTimer) {
        clearTimeout(this._resultTimer)
        this._resultTimer = null
      }
    },
  },

  pageLifetimes: {
    show() {
      this.startShakeListener()
    },
    hide() {
      this.stopShakeListener()
    },
  },

  methods: {
    // 真机"摇一摇"：监听加速度计，剧烈摇动触发与按钮相同的抽取流程。
    startShakeListener() {
      if (this._shakeListening) return
      this._shakeListening = true
      this._lastShakeAt = 0
      this._handleAcceleration = (res) => this.handleAcceleration(res)
      wx.startAccelerometer({ interval: 'ui', fail: () => {} })
      wx.onAccelerometerChange(this._handleAcceleration)
    },
    stopShakeListener() {
      if (!this._shakeListening) return
      this._shakeListening = false
      wx.offAccelerometerChange(this._handleAcceleration)
      wx.stopAccelerometer({ fail: () => {} })
    },
    handleAcceleration(res) {
      const force = Math.abs(res.x) + Math.abs(res.y) + Math.abs(res.z)
      // 静止时三轴合力约 1g，剧烈摇动会显著超过阈值
      if (force < 2.2) return
      const now = Date.now()
      if (now - (this._lastShakeAt || 0) < 1500) return
      this._lastShakeAt = now
      this.onShake()
    },

    // 把服务端 progress 响应映射为页面 data；结果弹窗相关字段由调用方补充
    mapProgressData(progress) {
      const state = progress.state
      return {
        completed: progress.completed,
        total: progress.total,
        finished: progress.finished,

        dailyFreeDraws: state.dailyFreeDraws,
        drawsUsed: state.drawsUsed,
        remainingDraws: state.remainingDraws,
        shareBonusClaimed: state.shareBonusClaimed,
        canClaimShareBonus: state.canClaimShareBonus,

        collectedFrogIds: state.collectedFrogIds,
        collection: state.collection.map(item => ({
          ...item,
          assetUrl: resolveAssetUrl(item.assetUrl)
        })),
        badges: state.badges,

        activeChallenge: state.activeChallenge ? {
          ...state.activeChallenge,
          assetUrl: resolveAssetUrl(state.activeChallenge.assetUrl)
        } : null,
      }
    },

    // 加载游戏进度
    async loadProgress() {
      try {
        this.setData({ loading: true, error: null })
        const progress = await auth.withLogin(() => gamesService.getGameProgress(this.data.gameId))

        this.setData({
          loading: false,
          ...this.mapProgressData(progress),
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

        // 幂等键在一次"摇一摇"生命周期内保持不变：网络超时后重试会命中
        // 服务端去重；拿到服务端结论后清空，下一次摇一摇是新的逻辑操作。
        if (!this._drawEventKey) {
          this._drawEventKey = `guardian-draw-${Date.now()}-${Math.random().toString(36).slice(2)}`
        }
        const result = await auth.withLogin(() => gamesService.submitGameEvent(
          this.data.gameId,
          { type: 'draw' },
          this._drawEventKey
        ))
        this._drawEventKey = null

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
      const action = result.action

      this.setData({
        ...this.mapProgressData(result.progress),
        selectedPattern: null,
      })

      if (action.drawn) {
        // 成功抽到守护神，等待用户答题
        wx.showToast({
          title: action.message,
          icon: 'success',
          duration: 1500
        })
      } else {
        wx.showToast({
          title: action.message,
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

        // 同一道题的幂等键固定为 roundId：超时重试命中服务端去重，返回已记录的结果。
        const result = await auth.withLogin(() => gamesService.submitGameEvent(
          this.data.gameId,
          {
            type: 'answer',
            payload: {
              roundId: activeChallenge.roundId,
              pattern: selectedPattern
            }
          },
          `guardian-answer-${activeChallenge.roundId}`
        ))

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
      const action = result.action

      if (action.correct) {
        // 答对了，处理guardianCard的图片URL
        const guardianCard = action.guardianCard
        if (guardianCard) {
          guardianCard.assetUrl = resolveAssetUrl(guardianCard.assetUrl)
        }

        this.setData({
          ...this.mapProgressData(result.progress),
          selectedPattern: null,

          showResult: true,
          resultType: 'success',
          resultMessage: action.message,
          guardianCard: guardianCard || null,
          newlyUnlockedBadges: action.newlyUnlockedBadges,
        })

        wx.vibrateShort({ type: 'heavy' })

        // 3秒后自动关闭结果弹窗
        if (this._resultTimer) clearTimeout(this._resultTimer)
        this._resultTimer = setTimeout(() => {
          this._resultTimer = null
          this.setData({ showResult: false })
        }, 3000)
      } else {
        // 答错了
        wx.showToast({
          title: action.message,
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

        // 分享奖励每天一次，幂等键按天固定：同一天的重复请求直接命中服务端去重。
        const now = new Date()
        const dayKey = `${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate()}`
        const result = await auth.withLogin(() => gamesService.submitGameEvent(
          this.data.gameId,
          { type: 'share' },
          `guardian-share-${dayKey}`
        ))

        this.setData({
          ...this.mapProgressData(result.progress),
        })

        wx.showToast({
          title: result.action.message,
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

    // 页面级分享配置：Component 构造的页面必须放在 methods 内才会生效。
    onShareAppMessage() {
      // 分享成功后自动领取奖励
      this.claimShareBonus()

      return {
        title: '林蛙守护神 - 摇一摇解锁守护神',
        path: '/pages/games/guardian/guardian'
      }
    },

    onShareTimeline() {
      // 分享到朋友圈
      this.claimShareBonus()

      return {
        title: '林蛙守护神 - 摇一摇解锁守护神'
      }
    },
  },
})


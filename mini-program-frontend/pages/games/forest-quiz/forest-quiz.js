const auth = require('../../../utils/auth')
const quizService = require('../../../services/forest-quiz')

Page({
  data: {
    loading: true,
    submitting: false,
    error: null,
    progress: null,
    selectedOptionId: '',
    feedback: null,
    reviewLevel: null,
  },

  onLoad() {
    this.loadProgress()
  },

  loadProgress() {
    this.setData({ loading: true, error: null })
    auth.withLogin(() => quizService.getProgress())
      .then((progress) => this.setData({ progress, selectedOptionId: '', feedback: null, reviewLevel: null }))
      .catch((error) => this.setData({ error: error.message || '问答进度加载失败' }))
      .finally(() => this.setData({ loading: false }))
  },

  selectOption(event) {
    if (this.data.submitting) return
    this.setData({ selectedOptionId: event.currentTarget.dataset.optionId, feedback: null })
  },

  openLevel(event) {
    const levelId = event.currentTarget.dataset.levelId
    const item = this.data.progress.state.levels.find((level) => level.id === levelId)
    if (!item || !item.unlocked) return
    if (!item.completed) {
      this.setData({ reviewLevel: null })
      return
    }
    // 回顾内容（含正确答案标记）只来自服务端下发的 unlockedLevels。
    const reviewLevel = this.data.progress.state.unlockedLevels.find((level) => level.id === levelId)
    if (!reviewLevel) throw new Error(`已通关关卡 ${levelId} 缺少回顾内容`)
    this.setData({ reviewLevel, selectedOptionId: '', feedback: null })
  },

  closeReview() {
    this.setData({ reviewLevel: null })
  },

  submitAnswer() {
    if (this.data.reviewLevel) return
    const current = this.data.progress.state.currentLevel
    const index = this.data.progress.state.currentQuestionIndex
    const question = current && current.questions ? current.questions[index] : null
    if (!current || !question || !this.data.selectedOptionId || this.data.submitting) return

    this.setData({ submitting: true })
    // 幂等键由"关卡 + 题目 + 选项"唯一决定：超时重试命中服务端去重，换选项则是新的逻辑操作。
    quizService.submitAnswer(current.id, question.id, this.data.selectedOptionId,
      `quiz-${current.id}-${question.id}-${this.data.selectedOptionId}`)
      .then((result) => {
        this.setData({ progress: result.progress, feedback: result.action })
        if (result.action.correct) {
          wx.vibrateShort({ type: 'light' })
          setTimeout(() => {
            this.setData({ selectedOptionId: '', feedback: null })
          }, 900)
        }
      })
      .catch((error) => wx.showToast({ title: error.message || '提交失败，请重试', icon: 'none' }))
      .finally(() => this.setData({ submitting: false }))
  },

  continueAfterWrong() {
    if (this.data.feedback && !this.data.feedback.correct) this.setData({ feedback: null, selectedOptionId: '' })
  },

  onShareAppMessage() {
    return { title: '我正在挑战林蛙知识闯关', path: '/pages/games/forest-quiz/forest-quiz' }
  },
})

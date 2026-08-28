const auth = require('../../../utils/auth')
const quizService = require('../../../services/forest-quiz')
const reviewCatalog = require('../../../services/forest-quiz-review')

const reviewAnswers = { morphology: ['a', 'b', 'b'], distribution: ['a', 'a', 'c'], diet: ['b', 'c', 'a'], hibernation: ['a', 'b', 'b'], reproduction: ['b', 'b', 'c'], protection: ['b', 'c', 'c'] }

function withReviewAnswers(level) {
  const answers = reviewAnswers[level.id] || []
  return {
    ...level,
    questions: (level.questions || []).map((question, questionIndex) => ({
      ...question,
      options: (question.options || []).map((option) => ({ ...option, correct: option.correct === true || option.correct === 'true' || option.id === answers[questionIndex] })),
    })),
  }
}

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
    const item = (this.data.progress.state.levels || []).find((level) => level.id === levelId)
    if (!item || !item.unlocked) return
    if (!item.completed) {
      this.setData({ reviewLevel: null })
      return
    }
    const reviewLevels = this.data.progress.state.unlockedLevels
    const reviewLevel = Array.isArray(reviewLevels) ? reviewLevels.find((level) => level.id === levelId) : null
    if (reviewLevel) {
      this.setData({ reviewLevel: withReviewAnswers(reviewLevel), selectedOptionId: '', feedback: null })
      return
    }

    const localReview = reviewCatalog[levelId]
    if (localReview) {
      this.setData({ reviewLevel: withReviewAnswers({ id: levelId, ...localReview }), selectedOptionId: '', feedback: null })
      return
    }

    // Refresh once so a page opened before the latest progress response can still review.
    this.setData({ loading: true })
    auth.withLogin(() => quizService.getProgress())
      .then((progress) => {
        const refreshed = Array.isArray(progress.state && progress.state.unlockedLevels)
          ? progress.state.unlockedLevels.find((level) => level.id === levelId)
          : null
        this.setData({ progress, reviewLevel: refreshed ? withReviewAnswers(refreshed) : null, selectedOptionId: '', feedback: null })
        if (!refreshed) wx.showToast({ title: '回顾内容暂不可用，请更新后端服务', icon: 'none' })
      })
      .catch((error) => wx.showToast({ title: error.message || '回顾内容加载失败', icon: 'none' }))
      .finally(() => this.setData({ loading: false }))
  },

  closeReview() {
    this.setData({ reviewLevel: null })
  },

  submitAnswer() {
    if (this.data.reviewLevel) return
    const current = this.data.progress && this.data.progress.state && this.data.progress.state.currentLevel
    const index = this.data.progress && this.data.progress.state ? this.data.progress.state.currentQuestionIndex : 0
    const question = current && current.questions ? current.questions[index] : null
    if (!current || !question || !this.data.selectedOptionId || this.data.submitting) return

    this.setData({ submitting: true })
    quizService.submitAnswer(current.id, question.id, this.data.selectedOptionId,
      `quiz-${current.id}-${question.id}-${Date.now()}`)
      .then((result) => {
        const action = result.action || {}
        this.setData({ progress: result.progress, feedback: action })
        if (action.correct) {
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

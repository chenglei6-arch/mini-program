const homeService = require('../../services/home')

Component({
  data: {
    loading: true,
    refreshing: false,
    summary: null,
  },
  lifetimes: {
    attached() {
      this.loadSummary()
    },
  },
  methods: {
    loadSummary() {
      this.setData({ loading: true, refreshing: true })
      homeService.getHomeSummary()
        .then((summary) => this.setData({ summary }))
        .catch(() => wx.showToast({ title: '首页内容加载失败', icon: 'none' }))
        .finally(() => this.setData({ loading: false, refreshing: false }))
    },
    openGame(event) {
      wx.navigateTo({ url: event.currentTarget.dataset.path })
    },
    openWelfare() {
      wx.navigateTo({ url: '/pages/welfare/welfare' })
    },
    openRankings() {
      wx.navigateTo({ url: '/pages/rankings/rankings' })
    },
    scanUnlock() {
      wx.navigateTo({ url: '/pages/scan/scan' })
    },
    onShareAppMessage() {
      return { title: '纸韵蛙鸣·哈什蚂传奇', path: '/pages/index/index' }
    },
    onShareTimeline() {
      return { title: '拼一张剪纸，听一段说部' }
    },
  },
})

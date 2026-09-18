const auth = require('../../utils/auth')
const homeService = require('../../services/home')
const contentService = require('../../services/content')

Component({
  data: {
    loading: true,
    refreshing: false,
    summary: null,
    detailVisible: false,
    detailLoading: false,
    frogDetail: null,
    // 吉林文旅宣传轮播（G331 沿线 / 长白山），正式图片素材到位后替换为图文数据
    tourSlides: [
      { theme: 'mountain', title: '长白山天池', copy: '火山湖映雪，林海听涛，走进满族发源地的四季' },
      { theme: 'river', title: 'G331 边境风光道', copy: '沿江而行，串起林蛙谷、湿地与边城村落' },
      { theme: 'forest', title: '林蛙谷秘境', copy: '哈什蚂的故乡，说部传唱千年的山林剧场' },
    ],
  },
  lifetimes: {
    attached() {
      this.loadSummary()
    },
  },
  methods: {
    loadSummary() {
      this.setData({ loading: true, refreshing: true })
      console.log('开始加载首页数据...')
      auth.withLogin(() => homeService.getHomeSummary())
        .then((summary) => {
          console.log('首页数据加载成功:', summary)
          this.setData({ summary })
        })
        .catch((error) => {
          console.error('首页数据加载失败:', error)
          wx.showToast({ title: error.message || '首页内容加载失败', icon: 'none', duration: 3000 })
        })
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
    openFrogDetail(event) {
      const id = event.currentTarget.dataset.id
      this.setData({ detailVisible: true, detailLoading: true, frogDetail: null })
      auth.withLogin(() => contentService.getFrogDetail(id))
        .then((frogDetail) => this.setData({ frogDetail }))
        .catch((error) => {
          this.setData({ detailVisible: false })
          wx.showToast({ title: error.message || '林蛙说明加载失败', icon: 'none' })
        })
        .finally(() => this.setData({ detailLoading: false }))
    },
    closeFrogDetail() {
      this.setData({ detailVisible: false, frogDetail: null })
    },
    stopDetailTap() {},
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

const homeService = require('../../services/home')

Component({
  data: { loading: true, summary: null },
  lifetimes: {
    attached() {
      homeService.getWelfareSummary()
        .then((summary) => this.setData({ summary }))
        .catch(() => wx.showToast({ title: '公益信息加载失败', icon: 'none' }))
        .finally(() => this.setData({ loading: false }))
    },
  },
  methods: {
    openRankings() { wx.navigateTo({ url: '/pages/rankings/rankings' }) },
  },
})

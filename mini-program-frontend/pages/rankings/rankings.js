const auth = require('../../utils/auth')
const userService = require('../../services/user')

Component({
  data: { type: 'total', loading: true, data: null },
  lifetimes: { attached() { this.load() } },
  methods: {
    switchType(event) {
      this.setData({ type: event.currentTarget.dataset.type }, () => this.load())
    },
    load() {
      this.setData({ loading: true })
      auth.withLogin(() => userService.getRankings(this.data.type))
        .then((data) => this.setData({ data }))
        .catch(() => wx.showToast({ title: '榜单加载失败', icon: 'none' }))
        .finally(() => this.setData({ loading: false }))
    },
  },
})

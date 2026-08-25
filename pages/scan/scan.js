const auth = require('../../utils/auth')
const gameService = require('../../services/games')

Component({
  data: { code: '', loading: false, result: null },
  methods: {
    startScan() {
      wx.scanCode({
        onlyFromCamera: false,
        success: ({ result }) => this.redeem(result),
        fail: (error) => {
          if (error.errMsg && error.errMsg.indexOf('cancel') >= 0) return
          wx.showToast({ title: '未完成扫码', icon: 'none' })
        },
      })
    },
    redeem(code) {
      if (!code || this.data.loading) return
      this.setData({ loading: true, code })
      auth.withLogin(() => gameService.unlockByCode(code))
        .then((result) => this.setData({ result }))
        .catch(() => wx.showToast({ title: '二维码核验失败', icon: 'none' }))
        .finally(() => this.setData({ loading: false }))
    },
  },
})

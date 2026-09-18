const auth = require('../../utils/auth')
const gameService = require('../../services/games')

Component({
  data: { code: '', loading: false, result: null, error: '' },
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
    inputCode(event) {
      this.setData({ code: event.detail.value })
    },
    redeemManual() {
      this.redeem(this.data.code)
    },
    redeem(code) {
      if (!code || this.data.loading) return
      this.setData({ loading: true, code, error: '', result: null })
      auth.withLogin(() => gameService.unlockByCode(code))
        .then((result) => this.setData({ result }))
        .catch((error) => {
          const message = error && error.payload && error.payload.message
          this.setData({ error: message || '二维码核验失败，请重试' })
        })
        .finally(() => this.setData({ loading: false }))
    },
  },
})

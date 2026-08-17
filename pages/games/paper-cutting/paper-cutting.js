// 小程序运行时不做目录 index.js 自动解析，使用显式模块文件路径。
const { patterns } = require('../../../constants/index.js')
const gameService = require('../../../services/games')

Component({
  data: {
    pieces: patterns.map((name, index) => ({ id: `pattern-${index + 1}`, name, placed: false })),
    placedCount: 0,
    completed: false,
  },
  methods: {
    placePiece(event) {
      const index = Number(event.currentTarget.dataset.index)
      if (this.data.pieces[index].placed || this.data.completed) return
      const key = `pieces[${index}].placed`
      const placedCount = this.data.placedCount + 1
      this.setData({ [key]: true, placedCount })
      wx.vibrateShort({ type: 'light' })
      if (placedCount === this.data.pieces.length) this.completeGame()
    },
    completeGame() {
      gameService.submitGameEvent('paper-cutting', { type: 'completed', patternCount: this.data.pieces.length })
        .then(() => {
          this.setData({ completed: true })
          wx.showToast({ title: '剪纸拼合完成', icon: 'success' })
        })
        .catch(() => wx.showToast({ title: '进度保存失败', icon: 'none' }))
    },
    resetGame() {
      this.setData({
        pieces: this.data.pieces.map((item) => ({ ...item, placed: false })),
        placedCount: 0,
        completed: false,
      })
    },
    onShareAppMessage() {
      return { title: '我完成了一幅满族剪纸', path: '/pages/games/paper-cutting/paper-cutting' }
    },
  },
})

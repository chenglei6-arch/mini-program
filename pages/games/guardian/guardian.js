const mock = require('../../../services/mock-data')
const gameService = require('../../../services/games')

Component({
  data: {
    drawing: false,
    frog: null,
    options: [],
    result: '',
  },
  methods: {
    draw() {
      if (this.data.drawing) return
      this.setData({ drawing: true, frog: null, options: [], result: '' })
      wx.vibrateShort({ type: 'medium' })
      setTimeout(() => {
        const frog = mock.frogs[Math.floor(Math.random() * mock.frogs.length)]
        const alternatives = mock.frogs.filter((item) => item.pattern !== frog.pattern).slice(0, 2).map((item) => item.pattern)
        const options = [frog.pattern, ...alternatives].sort(() => Math.random() - 0.5)
        this.setData({ drawing: false, frog, options })
      }, 650)
    },
    answer(event) {
      const correct = event.currentTarget.dataset.pattern === this.data.frog.pattern
      this.setData({ result: correct ? 'correct' : 'wrong' })
      if (correct) {
        gameService.submitGameEvent('guardian', { type: 'unlocked', frogId: this.data.frog.id })
          .then(() => wx.showToast({ title: '守护成功', icon: 'success' }))
          .catch(() => wx.showToast({ title: '进度保存失败', icon: 'none' }))
      } else {
        wx.showToast({ title: '再试一次', icon: 'none' })
      }
    },
  },
})

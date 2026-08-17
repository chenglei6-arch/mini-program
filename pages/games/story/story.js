const gameService = require('../../../services/games')

const chapters = [
  { id: 1, title: '山谷初醒', status: 'available' },
  { id: 2, title: '林间回声', status: 'available' },
  { id: 3, title: '云上的歌', status: 'available' },
  { id: 4, title: '水纹的路', status: 'available' },
  { id: 5, title: '归山之夜', status: 'available' },
]

Component({
  data: {
    chapters,
    activeChapter: null,
    narrative: '',
    choices: [],
  },
  methods: {
    openChapter(event) {
      const chapter = chapters[Number(event.currentTarget.dataset.index)]
      this.setData({
        activeChapter: chapter,
        narrative: '晨雾从长白山的林间升起。哈什蚂听见远处传来一段古老的歌声，它需要决定下一步前往何处。',
        choices: ['沿溪流寻找声音', '登上山坡观察林谷', '留在原地辨认纹样'],
      })
    },
    choose(event) {
      const choice = event.currentTarget.dataset.choice
      gameService.submitGameEvent('story', { type: 'choice', chapterId: this.data.activeChapter.id, choice })
        .then(() => wx.showToast({ title: '选择已记录', icon: 'none' }))
    },
  },
})

// 小程序运行时不做目录 index.js 自动解析，使用显式模块文件路径。
const { patterns } = require('../../../constants/index.js')
const auth = require('../../../utils/auth')
const gameService = require('../../../services/games')
const { createPuzzleLayout } = require('../../../utils/puzzle-layout.js')

const RPX_BASE_WIDTH = 750
const SIDE_PADDING_RPX = 64
const DEFAULT_PUZZLE_COUNT = patterns.length
// 后端下发拼图配置后，可将配置数量传入 initPuzzle(count)，布局和拖动判定无需改动。

Component({
  data: {
    ready: false,
    stageWidth: 0,
    stageHeight: 0,
    puzzleCount: DEFAULT_PUZZLE_COUNT,
    boardHeight: 0,
    paletteTitleTop: 0,
    paletteSubtitleTop: 0,
    slots: [],
    pieces: [],
    placedCount: 0,
    completed: false,
    submitting: false,
  },

  lifetimes: {
    attached() {
      this.initPuzzle()
    },
  },

  methods: {
    initPuzzle(requestedCount) {
      const systemInfo = wx.getSystemInfoSync()
      const scale = systemInfo.windowWidth / RPX_BASE_WIDTH
      const sidePadding = SIDE_PADDING_RPX * scale
      const stageWidth = Math.max(systemInfo.windowWidth - sidePadding, 280 * scale)
      const layout = createPuzzleLayout({
        count: requestedCount || DEFAULT_PUZZLE_COUNT,
        stageWidth,
        scale,
        patternNames: patterns,
      })

      this.setData({
        ready: true,
        stageWidth,
        stageHeight: layout.stageHeight,
        puzzleCount: layout.count,
        boardHeight: layout.boardHeight,
        paletteTitleTop: layout.paletteTitleTop,
        paletteSubtitleTop: layout.paletteSubtitleTop,
        slots: layout.slots,
        pieces: layout.pieces,
      })
    },

    onPieceTouchStart(event) {
      this.activePieceIndex = Number(event.currentTarget.dataset.index)
    },

    onPieceChange(event) {
      const index = Number(event.currentTarget.dataset.index)
      const piece = this.data.pieces[index]
      if (!piece || piece.placed) return
      const x = Number(event.detail.x)
      const y = Number(event.detail.y)
      this.setData({
        [`pieces[${index}].x`]: x,
        [`pieces[${index}].y`]: y,
      })
      if (event.detail.source === 'touch') this.scheduleEvaluate(index)
    },

    onPieceTouchEnd() {
      const index = this.activePieceIndex
      this.activePieceIndex = null
      if (index === null || index === undefined) return
      this.scheduleEvaluate(index)
    },

    scheduleEvaluate(index) {
      // change 事件在不同基础库版本中可能连续触发，合并到最后一次位置后再判定。
      if (this.evaluateTimer) clearTimeout(this.evaluateTimer)
      this.evaluateTimer = setTimeout(() => {
        this.evaluateTimer = null
        this.evaluatePiece(index)
      }, 80)
    },

    evaluatePiece(index) {
      const piece = this.data.pieces[index]
      const slot = this.data.slots[index]
      if (!piece || piece.placed || !slot || this.data.completed) return

      const centerX = piece.x + piece.width / 2
      const centerY = piece.y + piece.height / 2
      const isInside = centerX >= slot.left && centerX <= slot.left + slot.width
        && centerY >= slot.top && centerY <= slot.top + slot.height

      if (!isInside) {
        this.setData({
          [`pieces[${index}].x`]: piece.initialX,
          [`pieces[${index}].y`]: piece.initialY,
        })
        wx.showToast({ title: '放置位置不正确', icon: 'none' })
        return
      }

      const placedCount = this.data.placedCount + 1
      const snapX = slot.left + (slot.width - piece.width) / 2
      const snapY = slot.top + (slot.height - piece.height) / 2
      this.setData({
        [`pieces[${index}].x`]: snapX,
        [`pieces[${index}].y`]: snapY,
        [`pieces[${index}].placed`]: true,
        [`slots[${index}].filled`]: true,
        [`slots[${index}].filledName`]: piece.name,
        placedCount,
      })
      wx.vibrateShort({ type: 'light' })
      if (placedCount === this.data.pieces.length) this.completeGame()
    },

    completeGame() {
      if (this.data.submitting || this.data.completed) return
      this.setData({ submitting: true })
      // 当前用本地几何判定提供即时反馈，接入正式接口后应由服务端复核完成结果。
      auth.withLogin(() => gameService.submitGameEvent('paper-cutting', { type: 'completed', patternCount: this.data.pieces.length }))
        .then(() => {
          this.setData({ completed: true, submitting: false })
          wx.showToast({ title: '剪纸拼合完成', icon: 'success' })
        })
        .catch(() => {
          this.setData({ submitting: false })
          wx.showToast({ title: '进度保存失败', icon: 'none' })
        })
    },

    resetGame() {
      this.setData({
        pieces: this.data.pieces.map((item) => ({
          ...item,
          x: item.initialX,
          y: item.initialY,
          placed: false,
        })),
        slots: this.data.slots.map((item) => ({ ...item, filled: false, filledName: '' })),
        placedCount: 0,
        completed: false,
        submitting: false,
      })
    },

    onShareAppMessage() {
      return { title: '我完成了一幅满族剪纸', path: '/pages/games/paper-cutting/paper-cutting' }
    },
  },
})

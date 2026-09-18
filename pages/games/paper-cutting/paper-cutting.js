const auth = require('../../../utils/auth')
const gameService = require('../../../services/games')
const contentService = require('../../../services/content')
const { resolveAssetUrl } = require('../../../utils/assets')
const sound = require('../../../utils/sound')

Component({
  data: {
    // 所有可选青蛙列表
    availableFrogs: [],

    // 当前选中的青蛙
    selectedFrogId: null,
    selectedFrogName: '',

    // 当前青蛙的组件列表
    frogComponents: [],
    totalComponents: 0,

    // 已放置的组件
    placedComponents: [],
    nextZIndex: 1,

    // 完成状态
    completed: false,
    completedFrogImage: '',

    // 文化解读弹窗
    showCultureModal: false,
    cultureData: null
  },

  lifetimes: {
    attached() {
      this.loadFrogs()
    }
  },

  methods: {
    // 加载所有青蛙列表
    async loadFrogs() {
      try {
        const frogsData = await auth.withLogin(() => contentService.getFrogs())
        this.setData({
          availableFrogs: frogsData.items || []
        })
      } catch (error) {
        console.error('加载青蛙列表失败:', error)
        wx.showToast({ title: '加载失败', icon: 'none' })
      }
    },

    // 选择要拼的青蛙
    async onSelectFrog(e) {
      const frogId = e.currentTarget.dataset.frogId

      if (this.data.selectedFrogId === frogId) {
        return // 已选中，不重复处理
      }

      // 如果正在拼其他青蛙，提示确认
      if (this.data.placedComponents.length > 0) {
        const result = await this.showConfirm('切换青蛙', '当前进度将丢失，确认切换吗？')
        if (!result) return
      }

      try {
        wx.showLoading({ title: '加载中...' })

        // 获取青蛙组件列表
        const componentsData = await auth.withLogin(() => gameService.getFrogComponents(frogId))
        const frog = this.data.availableFrogs.find(f => f.id === frogId)

        // 处理组件图片URL
        const components = (componentsData.components || []).map(comp => ({
          ...comp,
          assetUrl: resolveAssetUrl(comp.assetUrl)
        }))

        this.setData({
          selectedFrogId: frogId,
          selectedFrogName: frog ? frog.name : '',
          frogComponents: components,
          totalComponents: componentsData.totalComponents || 0,
          placedComponents: [],
          nextZIndex: 1,
          completed: false
        })
        this._startedAt = Date.now()

        wx.hideLoading()
      } catch (error) {
        wx.hideLoading()
        console.error('加载组件失败:', error)
        wx.showToast({ title: '加载失败', icon: 'none' })
      }
    },

    // 点击添加组件
    onAddComponent(e) {
      const componentId = e.currentTarget.dataset.componentId
      const component = this.data.frogComponents.find(c => c.id === componentId)

      if (!component) return

      // 检查是否解锁
      if (!component.unlocked) {
        wx.showToast({ title: '组件未解锁', icon: 'none' })
        return
      }

      // 检查是否已添加
      const alreadyPlaced = this.data.placedComponents.some(c => c.id === componentId)
      if (alreadyPlaced) {
        wx.showToast({ title: '该组件已添加', icon: 'none' })
        return
      }

      // 添加到画布
      const newComponent = {
        id: componentId,
        name: component.name,
        assetUrl: resolveAssetUrl(component.assetUrl),
        zIndex: this.data.nextZIndex
      }

      const updatedComponents = [...this.data.placedComponents, newComponent]

      // 更新组件库中的状态
      const updatedFrogComponents = this.data.frogComponents.map(c => {
        if (c.id === componentId) {
          return { ...c, placed: true }
        }
        return c
      })

      this.setData({
        placedComponents: updatedComponents,
        frogComponents: updatedFrogComponents,
        nextZIndex: this.data.nextZIndex + 1
      })
      sound.play('click')

      // 检查是否完成
      if (updatedComponents.length === this.data.totalComponents) {
        this.onPuzzleComplete()
      }
    },

    // 点击已放置的组件（删除）
    async onComponentTap(e) {
      const index = e.currentTarget.dataset.index
      const component = this.data.placedComponents[index]

      if (!component) return

      const result = await this.showConfirm('删除组件', `确定删除「${component.name}」吗？`)
      if (!result) return

      // 从画布移除
      const updatedPlaced = this.data.placedComponents.filter((_, i) => i !== index)

      // 更新组件库状态
      const updatedFrogComponents = this.data.frogComponents.map(c => {
        if (c.id === component.id) {
          return { ...c, placed: false }
        }
        return c
      })

      this.setData({
        placedComponents: updatedPlaced,
        frogComponents: updatedFrogComponents
      })
    },

    // 拼图完成
    async onPuzzleComplete() {
      this.setData({ completed: true })

      sound.play('success')
      wx.vibrateShort({ type: 'heavy' })
      wx.showToast({ title: '拼图完成！', icon: 'success', duration: 2000 })

      // 获取完整青蛙图片
      const frog = this.data.availableFrogs.find(f => f.id === this.data.selectedFrogId)
      this.setData({
        completedFrogImage: frog ? frog.assetUrl : ''
      })

      // 提交完成记录
      this.submitCompletion()
    },

    // 保存完成的剪纸作品到相册
    saveArtwork() {
      const imageUrl = this.data.completedFrogImage
      if (!imageUrl) {
        wx.showToast({ title: '作品未就绪', icon: 'none' })
        return
      }
      wx.downloadFile({
        url: resolveAssetUrl(imageUrl),
        success: (download) => {
          wx.saveImageToPhotosAlbum({
            filePath: download.tempFilePath,
            success: () => wx.showToast({ title: '已保存到相册', icon: 'success' }),
            fail: (error) => {
              if (error.errMsg && error.errMsg.indexOf('auth') >= 0) {
                wx.showModal({
                  title: '需要相册权限',
                  content: '请在设置中允许保存图片到相册',
                  success: (res) => { if (res.confirm) wx.openSetting() }
                })
                return
              }
              wx.showToast({ title: '保存失败', icon: 'none' })
            }
          })
        },
        fail: () => wx.showToast({ title: '作品下载失败', icon: 'none' }),
      })
    },

    // 关闭完成提示，保留已拼好的画布
    onCloseComplete() {
      this.setData({ completed: false })
    },

    async submitCompletion() {
      try {
        const payload = {
          timeCost: this._startedAt ? Math.round((Date.now() - this._startedAt) / 1000) : 0,
          components: this.data.placedComponents.map(c => c.id)
        }

        await auth.withLogin(() =>
          gameService.submitFrogComplete(
            this.data.selectedFrogId,
            payload,
            // 每只蛙的完成事件幂等键固定：服务端本身对重复完成也做了去重，
            // 固定 key 让超时重试能拿到已记录的结果而不是报错。
            `frog-${this.data.selectedFrogId}`
          )
        )
      } catch (error) {
        console.error('提交完成记录失败:', error)
      }
    },

    // 查看文化解读
    async onViewCulture() {
      try {
        wx.showLoading({ title: '加载中...' })
        const frogDetail = await contentService.getFrogDetail(this.data.selectedFrogId)

        this.setData({
          showCultureModal: true,
          cultureData: frogDetail
        })

        wx.hideLoading()
      } catch (error) {
        wx.hideLoading()
        console.error('加载文化解读失败:', error)
        wx.showToast({ title: '加载失败', icon: 'none' })
      }
    },

    hideCultureModal() {
      this.setData({
        showCultureModal: false,
        cultureData: null
      })
    },

    stopPropagation() {
      // 阻止事件冒泡
    },

    // 拼其他青蛙
    onSelectAnother() {
      this.setData({
        selectedFrogId: null,
        selectedFrogName: '',
        frogComponents: [],
        totalComponents: 0,
        placedComponents: [],
        nextZIndex: 1,
        completed: false,
        completedFrogImage: ''
      })
    },

    // 工具函数：显示确认对话框
    showConfirm(title, content) {
      return new Promise((resolve) => {
        wx.showModal({
          title,
          content,
          success: (res) => {
            resolve(res.confirm)
          },
          fail: () => {
            resolve(false)
          }
        })
      })
    },

    onShareAppMessage() {
      return {
        title: `我完成了「${this.data.selectedFrogName || '青蛙拼图'}」`,
        path: '/pages/games/paper-cutting/paper-cutting'
      }
    }
  }
})

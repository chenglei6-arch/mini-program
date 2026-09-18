const auth = require('../../utils/auth')
const mallService = require('../../services/mall')

Component({
  data: {
    loading: true,
    refreshing: false,
    products: [],
  },
  lifetimes: {
    attached() {
      this.loadProducts()
    },
  },
  methods: {
    loadProducts() {
      this.setData({ loading: true, refreshing: true })
      auth.withLogin(() => mallService.getProducts())
        .then((data) => this.setData({ products: data.items }))
        .catch((error) => {
          console.error('商城商品加载失败:', error)
          wx.showToast({ title: error.message || '商城内容加载失败', icon: 'none', duration: 3000 })
        })
        .finally(() => this.setData({ loading: false, refreshing: false }))
    },
    openProduct(event) {
      wx.navigateTo({ url: `/pages/mall/order-create/order-create?id=${event.currentTarget.dataset.id}` })
    },
    openOrders() {
      wx.navigateTo({ url: '/pages/mall/orders/orders' })
    },
    onShareAppMessage() {
      return { title: '纸韵蛙鸣·林小蛙剪纸盲盒', path: '/pages/mall/mall' }
    },
  },
})

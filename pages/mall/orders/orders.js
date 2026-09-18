const auth = require('../../utils/auth')
const mallService = require('../../services/mall')

Page({
  data: {
    loading: true,
    refreshing: false,
    orders: [],
  },
  onLoad() {
    this.loadOrders()
  },
  loadOrders() {
    this.setData({ loading: true, refreshing: true })
    auth.withLogin(() => mallService.getOrders())
      .then((data) => this.setData({ orders: data.items }))
      .catch((error) => wx.showToast({ title: error.message || '订单加载失败', icon: 'none' }))
      .finally(() => this.setData({ loading: false, refreshing: false }))
  },
})

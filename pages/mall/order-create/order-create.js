const auth = require('../../utils/auth')
const mallService = require('../../services/mall')

function makeIdempotencyKey() {
  return 'order-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)
}

Page({
  data: {
    loading: true,
    product: null,
    quantity: 1,
    totalText: '',
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    submitting: false,
    successOrder: null,
  },

  onLoad(options) {
    this.idempotencyKey = makeIdempotencyKey()
    const productId = options && options.id
    if (!productId) {
      this.setData({ loading: false })
      wx.showToast({ title: '商品不存在', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 600)
      return
    }
    this.loadProduct(productId)
  },

  loadProduct(productId) {
    auth.withLogin(() => mallService.getProductDetail(productId))
      .then((product) => {
        this.setData({ product, totalText: this.computeTotalText(product, this.data.quantity) })
      })
      .catch((error) => {
        wx.showToast({ title: error.message || '商品加载失败', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 600)
      })
      .finally(() => this.setData({ loading: false }))
  },

  computeTotalText(product, quantity) {
    // 仅用于展示；订单总价以服务端计算为准。
    return mallService.formatCents(Number(product.priceCents) * quantity)
  },

  decreaseQuantity() {
    if (this.data.quantity <= 1) return
    this.applyQuantity(this.data.quantity - 1)
  },

  increaseQuantity() {
    if (this.data.quantity >= 99) return
    this.applyQuantity(this.data.quantity + 1)
  },

  applyQuantity(quantity) {
    this.setData({ quantity, totalText: this.computeTotalText(this.data.product, quantity) })
  },

  inputName(event) {
    this.setData({ receiverName: event.detail.value })
  },

  inputPhone(event) {
    this.setData({ receiverPhone: event.detail.value })
  },

  inputAddress(event) {
    this.setData({ receiverAddress: event.detail.value })
  },

  submitOrder() {
    const { product, quantity, receiverName, receiverPhone, receiverAddress, submitting } = this.data
    if (!product || submitting) return
    if (!receiverName.trim()) {
      wx.showToast({ title: '请填写收货人姓名', icon: 'none' })
      return
    }
    if (!/^1\d{10}$/.test(receiverPhone.trim())) {
      wx.showToast({ title: '请填写正确的手机号', icon: 'none' })
      return
    }
    if (!receiverAddress.trim()) {
      wx.showToast({ title: '请填写收货地址', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    // 同一幂等键重复提交不会重复建单，下单成功后才更换新键。
    auth.withLogin(() => mallService.createOrder({
      productId: product.id,
      quantity,
      receiverName: receiverName.trim(),
      receiverPhone: receiverPhone.trim(),
      receiverAddress: receiverAddress.trim(),
    }, this.idempotencyKey))
      .then((order) => {
        this.idempotencyKey = makeIdempotencyKey()
        this.setData({ successOrder: order })
      })
      .catch((error) => wx.showToast({ title: error.message || '下单失败，请重试', icon: 'none' }))
      .finally(() => this.setData({ submitting: false }))
  },

  openOrders() {
    wx.navigateTo({ url: '/pages/mall/orders/orders' })
  },

  backToMall() {
    wx.navigateBack()
  },
})

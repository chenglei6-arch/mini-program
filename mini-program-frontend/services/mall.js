const request = require('../utils/request')

// 订单状态文案；后端新增状态时必须同步这里，否则直接报错而不是显示原始状态码。
const STATUS_LABELS = {
  PENDING_PAYMENT: '待支付',
}

// 金额以分传输、在服务层格式化为展示文本；总价以服务端计算结果为准，页面只做展示。
// 金额或时间字段不符合契约时直接抛错，不返回空串把问题藏起来。
function formatCents(cents) {
  const value = Number(cents)
  if (!Number.isFinite(value)) throw new Error(`金额字段不是数字：${cents}`)
  return (value / 100).toFixed(2)
}

function formatDateTime(value) {
  const date = new Date(value)
  if (!value || Number.isNaN(date.getTime())) throw new Error(`时间字段无法解析：${value}`)
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function statusLabel(status) {
  const label = STATUS_LABELS[status]
  if (!label) throw new Error(`未知订单状态：${status}`)
  return label
}

function getProducts() {
  return request.request({ url: '/v1/mall/products' }).then((data) => ({
    ...data,
    items: data.items.map((item) => ({ ...item, priceText: formatCents(item.priceCents) })),
  }))
}

function getProductDetail(productId) {
  return request.request({ url: `/v1/mall/products/${encodeURIComponent(productId)}` }).then((item) => ({
    ...item,
    priceText: formatCents(item.priceCents),
  }))
}

function createOrder(payload, idempotencyKey) {
  return request.request({
    url: '/v1/mall/orders',
    method: 'POST',
    data: payload,
    header: { 'Idempotency-Key': idempotencyKey },
  }).then((order) => ({
    ...order,
    statusText: statusLabel(order.status),
    unitPriceText: formatCents(order.unitPriceCents),
    totalText: formatCents(order.totalCents),
    createdAtText: formatDateTime(order.createdAt),
  }))
}

function getOrders() {
  return request.request({ url: '/v1/mall/orders' }).then((data) => ({
    ...data,
    items: data.items.map((order) => ({
      ...order,
      statusText: statusLabel(order.status),
      totalText: formatCents(order.totalCents),
      createdAtText: formatDateTime(order.createdAt),
    })),
  }))
}

module.exports = { getProducts, getProductDetail, createOrder, getOrders, formatCents }

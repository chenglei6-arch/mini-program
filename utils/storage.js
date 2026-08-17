const prefix = 'jcb:'

function key(name) {
  return `${prefix}${name}`
}

function get(name, fallback = null) {
  try {
    const value = wx.getStorageSync(key(name))
    return value === '' || value === undefined ? fallback : value
  } catch (error) {
    console.warn('[storage] read failed', name, error)
    return fallback
  }
}

function set(name, value) {
  try {
    wx.setStorageSync(key(name), value)
    return true
  } catch (error) {
    console.warn('[storage] write failed', name, error)
    return false
  }
}

function remove(name) {
  try {
    wx.removeStorageSync(key(name))
    return true
  } catch (error) {
    console.warn('[storage] remove failed', name, error)
    return false
  }
}

module.exports = { get, set, remove }

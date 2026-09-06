// 轻量音效播放：项目自带的 WAV 音效（assets/audio），播放失败静默忽略，不影响主流程。
const contexts = {}

function play(name) {
  try {
    if (!contexts[name]) {
      const context = wx.createInnerAudioContext()
      context.src = `/assets/audio/${name}.wav`
      context.obeyMuteSwitch = true
      contexts[name] = context
    }
    const context = contexts[name]
    context.stop()
    context.play()
  } catch (error) {
    // 设备或基础库不支持时直接跳过
  }
}

module.exports = { play }

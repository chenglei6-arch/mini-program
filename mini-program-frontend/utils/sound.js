// 轻量音效播放：项目自带的 WAV 音效（assets/audio），播放异常直接抛出由调用方感知。
const contexts = {}

function play(name) {
  if (!contexts[name]) {
    const context = wx.createInnerAudioContext()
    context.src = `/assets/audio/${name}.wav`
    context.obeyMuteSwitch = true
    contexts[name] = context
  }
  const context = contexts[name]
  context.stop()
  context.play()
}

module.exports = { play }

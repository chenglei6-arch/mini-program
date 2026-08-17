// 微信小程序模块解析不保证 Node.js 的目录 index.js 自动解析，使用显式路径。
const { games, patterns } = require('../constants/index.js')

const frogs = [
  { id: 'forest', name: '护林蛙', shortName: '护', pattern: '山林纹', unlocked: true },
  { id: 'ginseng', name: '采参蛙', shortName: '采', pattern: '云纹', unlocked: false },
  { id: 'hibernation', name: '冬眠蛙', shortName: '冬', pattern: '水波纹', unlocked: false },
  { id: 'lotus', name: '荷叶蛙', shortName: '荷', pattern: '蛙纹', unlocked: false },
  { id: 'insect', name: '捕虫蛙', shortName: '捕', pattern: '虫鸟纹', unlocked: false },
  { id: 'immune', name: '免疫蛙', shortName: '免', pattern: '太阳纹', unlocked: false },
  { id: 'youth', name: '驻颜蛙', shortName: '驻', pattern: '花叶纹', unlocked: false },
  { id: 'snow', name: '天池映雪蛙', shortName: '雪', pattern: '冰雪纹', unlocked: false },
  { id: 'lung', name: '润肺蛙', shortName: '润', pattern: '空气纹', unlocked: false },
]

const badges = [
  { id: 'paper-beginner', name: '剪纸新手', level: 'bronze', unlocked: true },
  { id: 'story-listener', name: '故事聆听者', level: 'bronze', unlocked: false },
  { id: 'guardian-first', name: '守护神初遇', level: 'bronze', unlocked: false },
  { id: 'paper-master', name: '剪纸匠人', level: 'silver', unlocked: false },
  { id: 'story-inheritor', name: '说部传承人', level: 'silver', unlocked: false },
  { id: 'guardian-messenger', name: '守护神使者', level: 'silver', unlocked: false },
  { id: 'paper-artist', name: '剪纸大师', level: 'gold', unlocked: false },
  { id: 'story-guardian', name: '说部守护者', level: 'gold', unlocked: false },
  { id: 'nine-guardians', name: '九蛙守护者', level: 'gold', unlocked: false },
]

function home() {
  return {
    brand: '纸韵蛙鸣·哈什蚂传奇',
    slogan: '拼一张剪纸，听一段说部',
    fundAmount: '12,480.00',
    fundUpdateAt: '2026-08-17 12:00',
    games,
    frogs,
    patterns,
    activity: [
      '吉林·张同学刚刚集齐了第6枚徽章',
      '辽宁·李同学解锁了蛙纹',
      '黑龙江·王同学完成了林蛙谷第1章',
    ],
  }
}

function profile() {
  return {
    user: { id: 'mock-user', nickname: '体验用户', avatarUrl: '', isGuest: true },
    stats: { frogs: 1, frogsTotal: 9, patterns: 1, patternsTotal: 5, badges: 1, badgesTotal: 9, contribution: '1.00' },
    badges,
    orders: [],
  }
}

module.exports = { home, profile, frogs, badges }

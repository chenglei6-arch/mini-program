# 守护神游戏开发文档

## 功能说明

摇一摇抽奖游戏，收集9只林蛙守护神。

### 核心规则
- **每日次数：** 基础3次，分享+1次，最多4次
- **零点重置：** 按中国时区（Asia/Shanghai）零点自动重置
- **抽取策略：** 优先抽未收集的角色
- **答题机制：** 三选一，答错不扣次数
- **分享奖励：** 前端分享成功后自动调用后端接口领取
- **徽章系统：** 收集1/3/9只自动解锁铜/银/金徽章

## API 接口

### 获取进度
```
GET /v1/games/guardian/progress
```

### 摇一摇抽取
```
POST /v1/games/guardian/events
Header: Idempotency-Key
Body: { "type": "draw" }
```

### 提交答案
```
POST /v1/games/guardian/events
Header: Idempotency-Key
Body: { 
  "type": "answer",
  "payload": { "roundId": "...", "pattern": "蛙纹" }
}
```

### 领取分享奖励
```
POST /v1/games/guardian/events
Header: Idempotency-Key
Body: { "type": "share" }
```

## 图片URL处理

后端返回相对路径 `/assets/frogs/forest.png`，前端需要拼接 `baseUrl`。

**处理函数：**
```javascript
const env = require('../../../config/env')

function buildImageUrl(assetUrl) {
  if (!assetUrl) return ''
  if (/^https?:\/\//.test(assetUrl)) return assetUrl
  if (assetUrl.startsWith('/')) {
    return `${env.baseUrl.replace(/\/$/, '')}${assetUrl}`
  }
  return assetUrl
}
```

**使用位置：**
- `loadProgress()` - 加载进度时处理 collection 和 activeChallenge
- `handleDrawResult()` - 抽取结果时处理 activeChallenge
- `handleAnswerResult()` - 答题结果时处理 guardianCard 和 collection

## 文件结构

```
pages/games/guardian/
├── guardian.js      - 页面逻辑（328行）
├── guardian.wxml    - 页面结构（169行）
├── guardian.wxss    - 页面样式（370行）
└── guardian.json    - 页面配置
```

## 测试流程

1. 启动后端：`cd mini-program-backend && ./gradlew bootRun`
2. 配置前端：确认 `config/env.js` 中的 `baseUrl`
3. 打开微信开发者工具，导入前端项目
4. 进入守护神页面测试：
   - 摇一摇抽取
   - 三选一答题
   - 分享增加次数
   - 图鉴收集
   - 徽章解锁

## 常见问题

**Q: 图片加载失败？**  
A: 检查 `config/env.js` 中的 `baseUrl` 配置，确保后端服务已启动。开发环境可勾选"不校验合法域名"。

**Q: 答错题扣次数吗？**  
A: 不扣。只有答对才消耗次数并收集角色。

**Q: 分享后没增加次数？**  
A: 检查 `onShareAppMessage` 是否正确实现，确保调用了 `claimShareBonus()`。每天只能领取一次。

**Q: 跨天后没重置？**  
A: 后端使用 `Asia/Shanghai` 时区，检查服务器时区设置，或重新调用进度接口刷新。

## 数据库表

- `guardian_daily_state` - 每日状态（次数、分享领取状态）
- `guardian_collection` - 收集记录
- `guardian_round` - 当前题目
- `guardian_event` - 幂等事件

---

**开发完成时间：** 2024-08-27  
**前端代码：** 867行  
**功能完成度：** 100%

# 守护神游戏（摇一摇抽奖）测试文档

## 功能概述

守护神游戏是一个基于摇一摇的抽奖系统，用户通过摇一摇随机抽取林蛙角色，答对对应纹样题后即可收集该角色。

## 核心规则

### 1. 每日次数规则
- 基础次数：每天 3 次（按中国时区 Asia/Shanghai 零点重置）
- 分享奖励：分享后可额外获得 1 次
- 每日最多总共：4 次（3 次基础 + 1 次分享）

### 2. 角色抽取规则
- **优先抽未收集的角色**：系统会从未收集的林蛙中随机抽取
- 已收集的角色不会再次被抽到
- 收集完全部 9 只林蛙后，无法再抽取

### 3. 答题机制
- 抽到角色后，系统展示 3 个纹样选项（1 个正确 + 2 个干扰项）
- 用户需要选择正确的纹样
- **答错不消耗次数**，可以再次尝试
- 答对后：
  - 消耗本次抽取次数
  - 将该角色加入图鉴
  - 检查是否解锁新徽章
  - 清除当前题目

### 4. 分享机制
- 用户点击"分享获得额外次数"按钮
- 完成微信分享操作（分享给好友或朋友圈）
- **前端在分享回调中自动调用后端接口领取奖励**
- 后端不做额外校验，直接发放额外次数
- 每天只能领取一次分享奖励

### 5. 徽章系统
三枚守护神徽章：
- **守护神初遇**（铜牌）：收集 1 只林蛙
- **守护神使者**（银牌）：收集 3 只林蛙
- **九蛙守护者**（金牌）：收集全部 9 只林蛙

## API 接口

### 1. 获取游戏进度
```http
GET /v1/games/guardian/progress
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "gameId": "guardian",
    "completed": 2,
    "total": 9,
    "finished": false,
    "version": 1,
    "updatedAt": "2024-08-27T10:00:00Z",
    "state": {
      "gameDate": "2024-08-27",
      "dailyFreeDraws": 3,
      "drawsUsed": 1,
      "remainingDraws": 2,
      "shareBonusClaimed": false,
      "canClaimShareBonus": true,
      "collectedFrogIds": ["forest", "mountain"],
      "collection": [
        {
          "id": "forest",
          "name": "护林蛙",
          "shortName": "护林",
          "pattern": "蛙纹",
          "blessing": "守护森林食物链...",
          "assetUrl": "/assets/frogs/forest.png"
        }
      ],
      "activeChallenge": null,
      "badges": [
        {
          "id": "guardian-first",
          "name": "守护神初遇",
          "level": "bronze",
          "unlocked": true
        },
        {
          "id": "guardian-messenger",
          "name": "守护神使者",
          "level": "silver",
          "unlocked": false
        },
        {
          "id": "guardian-nine",
          "name": "九蛙守护者",
          "level": "gold",
          "unlocked": false
        }
      ]
    }
  }
}
```

### 2. 摇一摇抽取
```http
POST /v1/games/guardian/events
Authorization: Bearer {token}
Idempotency-Key: draw-{timestamp}-{random}
Content-Type: application/json

{
  "type": "draw"
}
```

**成功响应（抽到角色）：**
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "accepted": true,
    "duplicated": false,
    "gameId": "guardian",
    "progress": {
      "gameId": "guardian",
      "completed": 2,
      "total": 9,
      "finished": false,
      "version": 1,
      "updatedAt": "2024-08-27T10:00:00Z",
      "state": {
        "drawsUsed": 2,
        "remainingDraws": 1,
        "activeChallenge": {
          "roundId": "uuid-here",
          "frogId": "water",
          "name": "水源蛙",
          "shortName": "水源",
          "assetUrl": "/assets/frogs/water.png",
          "options": ["蛙纹", "太阳纹", "叶纹"],
          "createdAt": "2024-08-27T10:00:00Z"
        }
      }
    },
    "action": {
      "drawn": true,
      "message": "守护神已现身，请选择对应纹样"
    }
  }
}
```

**错误响应（次数用完）：**
```json
{
  "code": "COMMON_409",
  "message": "今日摇一摇次数已用完，分享后可额外获得 1 次"
}
```

**错误响应（有未完成题目）：**
```json
{
  "code": "0",
  "data": {
    "action": {
      "drawn": false,
      "message": "请先完成当前守护神纹样题"
    }
  }
}
```

### 3. 提交答案
```http
POST /v1/games/guardian/events
Authorization: Bearer {token}
Idempotency-Key: answer-{roundId}-{timestamp}
Content-Type: application/json

{
  "type": "answer",
  "payload": {
    "roundId": "uuid-here",
    "pattern": "蛙纹"
  }
}
```

**成功响应（答对）：**
```json
{
  "code": "0",
  "data": {
    "accepted": true,
    "duplicated": false,
    "gameId": "guardian",
    "progress": {
      "completed": 3,
      "state": {
        "collectedFrogIds": ["forest", "mountain", "water"],
        "collection": [...],
        "activeChallenge": null,
        "badges": [...]
      }
    },
    "action": {
      "correct": true,
      "newlyUnlocked": true,
      "guardianCard": {
        "id": "water",
        "name": "水源蛙",
        "shortName": "水源",
        "pattern": "蛙纹",
        "blessing": "守护湿地生态圈...",
        "assetUrl": "/assets/frogs/water.png"
      },
      "newlyUnlockedBadges": [
        {
          "id": "guardian-messenger",
          "name": "守护神使者",
          "level": "silver"
        }
      ],
      "message": "守护成功"
    }
  }
}
```

**失败响应（答错）：**
```json
{
  "code": "0",
  "data": {
    "action": {
      "correct": false,
      "message": "纹样不对，再试一次"
    }
  }
}
```

### 4. 领取分享奖励
```http
POST /v1/games/guardian/events
Authorization: Bearer {token}
Idempotency-Key: share-{timestamp}-{random}
Content-Type: application/json

{
  "type": "share"
}
```

**成功响应：**
```json
{
  "code": "0",
  "data": {
    "accepted": true,
    "duplicated": false,
    "gameId": "guardian",
    "progress": {
      "state": {
        "shareBonusClaimed": true,
        "canClaimShareBonus": false,
        "remainingDraws": 3
      }
    },
    "action": {
      "shareBonusGranted": 1,
      "message": "分享成功，已获得 1 次额外摇一摇机会"
    }
  }
}
```

**错误响应（已领取）：**
```json
{
  "code": "COMMON_409",
  "message": "今日分享额外次数已领取"
}
```

## 测试场景

### 场景 1：首次进入游戏
1. 调用进度接口，验证初始状态：
   - `completed = 0`
   - `remainingDraws = 3`
   - `shareBonusClaimed = false`
   - `collectedFrogIds = []`
   - `activeChallenge = null`

### 场景 2：摇一摇抽取并答题
1. 调用 draw 接口
2. 验证返回 `activeChallenge` 包含题目
3. 验证 `drawsUsed` 增加 1
4. 提交错误答案，验证不消耗次数
5. 提交正确答案，验证：
   - `collection` 增加该角色
   - `activeChallenge` 清空
   - 可能解锁新徽章

### 场景 3：分享获得额外次数
1. 验证 `canClaimShareBonus = true`
2. 调用 share 接口
3. 验证：
   - `shareBonusClaimed = true`
   - `canClaimShareBonus = false`
   - `remainingDraws` 增加 1
4. 再次调用 share 接口，验证返回 409 错误

### 场景 4：次数用完
1. 连续抽取 3 次（或 4 次如果已分享）
2. 验证 `remainingDraws = 0`
3. 再次调用 draw 接口，验证返回 409 错误

### 场景 5：跨天重置
1. 模拟到第二天（修改系统时区或等待零点）
2. 调用进度接口，验证：
   - `drawsUsed = 0`
   - `remainingDraws = 3`
   - `shareBonusClaimed = false`
   - `gameDate` 更新为新日期

### 场景 6：收集完所有角色
1. 持续抽取直到收集全部 9 只林蛙
2. 验证：
   - `completed = 9`
   - `finished = true`
   - 解锁"九蛙守护者"金牌徽章
3. 再次调用 draw 接口，验证返回 409 错误

### 场景 7：幂等性测试
1. 使用相同的 `Idempotency-Key` 重复调用接口
2. 验证返回结果一致，且 `duplicated = true`
3. 验证不会重复消耗次数或重复解锁角色

## 前端实现要点

### 1. 分享流程
```javascript
// 页面级分享配置
onShareAppMessage() {
  // 分享成功后自动领取奖励
  this.claimShareBonus()

  return {
    title: '林蛙守护神 - 摇一摇解锁守护神',
    path: '/pages/games/guardian/guardian',
  }
}

async claimShareBonus() {
  // 调用后端接口领取分享奖励
  const result = await gamesService.submitGameEvent(
    'guardian',
    { type: 'share' },
    idempotencyKey
  )
  // 更新UI状态
}
```

### 2. 摇一摇动画
```javascript
async onShake() {
  // 触发震动反馈
  wx.vibrateShort({ type: 'medium' })

  // 显示摇一摇动画
  this.setData({ shaking: true })

  // 调用后端接口
  const result = await gamesService.submitGameEvent(
    'guardian',
    { type: 'draw' },
    idempotencyKey
  )

  // 处理结果
}
```

### 3. 答题流程
```javascript
async onSubmitAnswer() {
  const result = await gamesService.submitGameEvent(
    'guardian',
    {
      type: 'answer',
      payload: {
        roundId: activeChallenge.roundId,
        pattern: selectedPattern
      }
    },
    idempotencyKey
  )

  if (result.action.correct) {
    // 答对：显示祝贺弹窗
    this.showSuccessModal(result.action.guardianCard)
  } else {
    // 答错：提示重试
    wx.showToast({ title: '纹样不对，再试一次' })
  }
}
```

## 数据库设计

### guardian_daily_state（每日状态）
- `user_id`: 用户ID
- `game_date`: 游戏日期（DATE）
- `draws_used`: 已使用次数
- `share_bonus_claimed`: 是否领取分享奖励

### guardian_collection（收集记录）
- `user_id`: 用户ID
- `frog_id`: 林蛙ID
- `collected_at`: 收集时间

### guardian_round（当前题目）
- `user_id`: 用户ID（唯一）
- `round_id`: 题目UUID
- `frog_id`: 林蛙ID
- `options_json`: 选项JSON数组
- `created_at`: 创建时间

### guardian_event（幂等事件）
- `user_id`: 用户ID
- `idempotency_key`: 幂等键（唯一）
- `event_type`: 事件类型（draw/answer/share）
- `response_json`: 响应JSON
- `created_at`: 创建时间

## 注意事项

1. **时区处理**：所有日期判断使用 `Asia/Shanghai` 时区
2. **幂等性**：所有写操作使用 `Idempotency-Key` 保证幂等
3. **优先级**：优先抽取未收集的角色
4. **答错不扣次数**：只有答对才消耗抽取次数
5. **分享无校验**：前端完成分享回调后直接调用后端接口
6. **每日重置**：零点自动重置，无需手动操作
7. **徽章自动解锁**：收集到对应数量自动解锁，无需额外操作

## 常见问题

### Q: 答错题是否消耗次数？
A: 不消耗。只有答对后才消耗次数并将角色加入图鉴。

### Q: 如何验证分享是否成功？
A: 前端在微信分享回调中自动调用后端接口，后端不做额外校验。

### Q: 收集完所有角色后还能摇一摇吗？
A: 不能。后端会返回 409 错误提示"九只林蛙守护神已全部收集"。

### Q: 跨天后之前的题目会怎样？
A: 当前题目不会自动清除，可以继续答题。新的一天次数会重置。

### Q: 如何测试时区重置逻辑？
A: 可以临时修改服务器系统时区，或等待实际零点到来。

# API 最小契约（待与 Apifox 对齐）

本文是页面与后端联调的边界，不是最终接口文档。正式开发前应以 Apifox 导出文件补齐字段、枚举和错误码。

## 通用约定

- Base URL 按环境配置，不允许写死在页面。
- 请求头：`Authorization: Bearer <accessToken>`；登录接口例外。
- 成功响应：`{ "code": "0", "data": {}, "message": "success", "requestId": "..." }`；失败响应：`{ "code": "BUSINESS_CODE", "data": null, "message": "...", "requestId": "..." }`。
  客户端只认 `code === "0"`，其余一律按错误抛出；响应不符合该结构同样抛错。
- 所有写操作需要幂等键或业务唯一键；支付回调必须幂等。
- 故事游戏事件使用 `{ "type": "story_choice", "payload": { "choiceId": "A" } }`，进度中的 `state.sceneId`、`state.sceneTitle`、`state.sceneParagraphs`、`state.choices` 和 `state.ending` 由服务端返回，客户端不得自行推导结局条件；场景正文只来自 `state.sceneParagraphs`。
- 服务端不做静默兜底：字段缺失、类型不符、JSON 损坏都会直接返回错误，客户端不做默认值填充与自动重试。

## 页面依赖接口

| 场景 | 方法 | 路径 | 备注 |
| --- | --- | --- | --- |
| 微信登录 | POST | `/v1/auth/wechat-login` | code 换取业务 Token |
| 首页聚合 | GET | `/v1/home/summary` | 基金、游戏、角色、动态 |
| 林蛙素材清单 | GET | `/v1/content/frogs` | 九只林蛙的纹样、祝福语和资源地址 |
| 林蛙详情 | GET | `/v1/content/frogs/{frogId}` | 大图及对应设计说明 DOCX 的章节内容 |
| 游戏进度 | GET | `/v1/games/{gameId}/progress` | 断点续玩 |
| 游戏事件 | POST | `/v1/games/{gameId}/events` | 服务端校验和去重；故事游戏使用 `story_choice` 事件 |
| 扫码核销 | POST | `/v1/unlocks/redeem` | 唯一码一次性核销 |
| 用户资料 | GET/PATCH | `/v1/me/profile` | 头像、昵称 |
| 公益摘要 | GET | `/v1/welfare/summary` | 基金金额和公示报告 |
| 排行榜 | GET | `/v1/rankings` | `type=total|weekly`，服务端分页/缓存 |
| 盲盒商品列表 | GET | `/v1/mall/products` | 商城 tab；金额以分（`priceCents`）返回 |
| 盲盒商品详情 | GET | `/v1/mall/products/{productId}` | 确认订单页 |
| 创建订单 | POST | `/v1/mall/orders` | 请求头带 `Idempotency-Key`；含收货人、手机号、地址；总价由服务端按商品单价计算，重复幂等键返回同一订单 |
| 我的订单 | GET | `/v1/mall/orders` | 订单列表，状态从 `PENDING_PAYMENT`（待支付）起步 |

## 尚未纳入页面的接口

- 库存、购物车、微信支付、退款和物流（微信支付受个人开发者备案限制，暂未接入）。
- 纹样详情、音频/视频签名 URL。
- 徽章授予、成就规则、公益证书生成。
- 举报、内容审核、运营后台和数据统计。

# 五子棋游戏设计文档

## 架构概览

五子棋游戏采用 WebSocket 实时通信架构，基于房间槽位（room slot）系统实现多人对战。设计宗旨：**简单、快速、轻量**。

### 核心原则

- **无需完整的 JWT 验证** — 用户仅需提供用户名即可加入
- **房间直连** — 用户指定房间 ID 直接加入，无需匹配等待
- **固定槽位** — 每个房间有固定数量的槽位（五子棋为 2 个）
- **状态继承** — 新用户加入空闲槽位时，继承该槽位的当前游戏数据
- **回合顺序** — 游戏回合跟随槽位顺序（slot 0 先手，slot 1 后手）

## 系统架构

```
Client (gomoku.html)
    │ WebSocket
    ▼
GomokuWebSocketHandler
    │ dispatch by message type
    ▼
GameRoomService
    │ manages rooms + game logic
    ▼
GameRoom (model)
    │ board state + slots
```

### 技术栈

- **后端**: Spring Boot 3.4.0 + WebSocket
- **前端**: 原生 JavaScript + Canvas
- **状态管理**: 内存 ConcurrentHashMap（无外部存储）
- **通信协议**: WebSocket JSON 消息

## 房间槽位系统

### GameRoom 模型

```java
public class GameRoom {
    public final String roomId;              // 房间 ID（用户指定）
    public final WebSocketSession[] sessions = new WebSocketSession[2];  // 槽位 0=黑方, 1=白方
    public final String[] playerIds = new String[2];  // 每个槽位的用户名
    public final int[][] board = new int[15][15];     // 棋盘状态
    public int turn = 0;                     // 当前回合（0=黑方, 1=白方）
    public boolean finished = false;         // 游戏是否结束
    public long disconnectedAt = -1;         // 断线时间戳
}
```

### 槽位分配逻辑

1. 用户发送 `join` 消息（包含 `username` 和 `roomId`）
2. 服务器查找或创建房间：`rooms.computeIfAbsent(roomId, GameRoom::new)`
3. 查找第一个空闲槽位：`sessions[i] == null || !sessions[i].isOpen()`
4. 分配槽位：`sessions[slot] = session`, `playerIds[slot] = username`
5. 返回当前游戏状态：`{ type: "joined", color, roomId, board, turn }`

### 状态继承

新用户加入时，自动继承槽位的当前游戏数据：
- **棋盘状态** (`board`) — 已落子的位置
- **当前回合** (`turn`) — 轮到哪方落子
- **颜色分配** — slot 0 = 黑方（先手），slot 1 = 白方（后手）

这意味着：如果 slot 0 的玩家断线，新玩家加入 slot 0 后，将以黑方身份继续游戏，棋盘保持原状。

## WebSocket 协议

### 客户端 → 服务器

| 消息类型 | 字段 | 说明 |
|---------|------|------|
| `join` | `username`, `roomId` | 加入指定房间 |
| `move` | `x`, `y` | 落子 |

### 服务器 → 客户端

| 消息类型 | 字段 | 说明 |
|---------|------|------|
| `joined` | `color`, `roomId`, `board`, `turn` | 加入成功，返回当前游戏状态 |
| `move` | `x`, `y`, `color` | 对手落子 |
| `win` | `color` | 游戏结束，某方获胜 |
| `draw` | — | 平局 |
| `opponent_left` | — | 对手断线 |
| `opponent_back` | — | 对手重连 |
| `error` | `message` | 错误（如房间已满） |

### 协议简化

相比原设计，新协议合并了三种消息：
- ❌ `waiting` — 等待对手加入
- ❌ `start` — 游戏开始
- ❌ `reconnected` — 重连恢复

统一为：
- ✅ `joined` — 加入房间，始终返回完整游戏状态

## 游戏流程

### 1. 进入游戏

```
用户输入 username + roomId
    ↓
WebSocket 连接 /ws/gomoku
    ↓
发送 { type: "join", username, roomId }
    ↓
服务器分配槽位
    ↓
返回 { type: "joined", color, board, turn }
```

### 2. 对战流程

```
黑方（slot 0）先手
    ↓
点击棋盘 → 发送 { type: "move", x, y }
    ↓
服务器验证 → 广播 { type: "move", x, y, color }
    ↓
检查胜负 → 五子连珠 → { type: "win", color }
    ↓
切换回合 → 白方（slot 1）落子
```

### 3. 断线重连

```
用户断线 → session[slot] = null
    ↓
对手收到 { type: "opponent_left" }
    ↓
用户重连 → 发送 { type: "join", username, roomId }
    ↓
服务器分配回原槽位（或新槽位）
    ↓
返回 { type: "joined", board, turn } — 游戏状态完整恢复
    ↓
对手收到 { type: "opponent_back" }
```

## 文件结构

```
src/main/java/zec/ghibli/zechat/module/game/
├── config/
│   └── GameWebSocketConfig.java          # WebSocket 端点配置
├── handler/
│   └── GomokuWebSocketHandler.java       # 消息分发
├── model/
│   └── GameRoom.java                     # 房间模型
└── service/
    └── GameRoomService.java              # 房间管理 + 游戏逻辑

src/main/resources/static/
└── gomoku.html                           # 游戏前端（Canvas 渲染）
```

## 关键设计决策

### 1. 为什么不用 JWT？

**原因**：游戏场景追求极简，JWT 的签名验证、过期检查、密钥管理都是不必要的复杂度。

**替代方案**：用户名 + 房间 ID 直接加入，WebSocket 连接本身即为会话凭证。

### 2. 为什么不用匹配系统？

**原因**：匹配系统需要维护等待队列、处理超时、匹配算法等，增加复杂度。

**替代方案**：用户自行指定房间 ID，朋友间共享房间号即可对战。

### 3. 为什么状态继承？

**原因**：简化重连逻辑。无需区分"首次加入"和"重连"，统一为"加入空闲槽位"。

**好处**：
- 代码更简单（单一 `join` 方法）
- 支持"接管"场景（A 断线，B 可接管 A 的位置继续游戏）
- 前端无需维护复杂的重连状态

### 4. 为什么内存存储？

**原因**：游戏会话短暂（通常 5-15 分钟），无需持久化。

**清理策略**：
- 定时任务每 60 秒清理断线超过 10 分钟的房间
- 游戏结束后，客户端清除 localStorage 中的房间 ID

### 5. 为什么 Canvas 渲染？

**原因**：
- 五子棋需要精确的棋盘绘制（网格、星位、棋子渐变）
- Canvas 性能优于 DOM 操作（15×15 = 225 个格子）
- 支持悬停预览、落子动画等交互效果

## 性能特性

- **并发安全**：`GameRoomService` 所有方法使用 `synchronized`
- **内存占用**：每个房间约 2KB（15×15 int 数组 + 2 个 WebSocket 引用）
- **消息延迟**：WebSocket 单向延迟 < 50ms（局域网）
- **自动清理**：10 分钟无活动的房间自动销毁

## 扩展性考虑

当前设计支持以下扩展（无需重构）：

1. **多种棋类** — 修改 `GameRoom.SIZE` 和胜负判定逻辑
2. **观战模式** — 增加 `observers[]` 数组，广播时包含观众
3. **房间列表** — 添加 `GET /api/rooms` 端点，返回 `rooms.keySet()`
4. **聊天功能** — 新增 `chat` 消息类型，广播给房间内所有人
5. **回放功能** — 记录 `moves[]` 历史，支持复盘

## 测试场景

### 基础流程
1. 两个用户加入同一房间 → 黑白方分配正确
2. 黑方落子 → 白方收到 `move` 消息
3. 五子连珠 → 双方收到 `win` 消息

### 边界情况
1. 第三个用户加入已满房间 → 收到 `error { message: "room full" }`
2. 用户断线 → 对手收到 `opponent_left`
3. 用户重连 → 棋盘状态完整恢复
4. 非法落子（已有棋子的位置）→ 收到 `error { message: "invalid move" }`
5. 非回合落子 → 收到 `error { message: "not your turn" }`

### 压力测试
- 100 个并发房间（200 个 WebSocket 连接）
- 每秒 50 次落子操作
- 内存占用 < 50MB

## 已知限制

1. **单机部署** — 无法水平扩展（WebSocket 连接绑定到单个进程）
2. **无持久化** — 服务器重启后所有游戏丢失
3. **无防作弊** — 客户端可伪造落子消息（需增加服务端验证）
4. **无超时机制** — 玩家可无限期占用房间（需增加回合计时器）

## 未来优化方向

1. **Redis 存储** — 支持多实例部署 + 游戏状态持久化
2. **WebSocket 集群** — 使用 Redis Pub/Sub 实现跨实例消息广播
3. **ELO 积分系统** — 记录玩家胜率，实现匹配算法
4. **AI 对手** — 集成 Minimax 算法，支持人机对战
5. **移动端适配** — 响应式布局 + 触摸事件优化

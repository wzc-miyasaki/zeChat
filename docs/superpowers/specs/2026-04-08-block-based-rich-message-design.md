# Block-Based Rich Message System — Design Spec

## 1. Overview

zeChat 当前仅支持纯文本聊天。本设计引入 block-based 消息协议，支持文本、图片、文件混合输入。图片走 Spring AI 多模态 API，文件提取文本拼入上下文。

### 数据流

```
chat3.html (contenteditable 编辑器)
    │ 1. 图片/文件先上传 → POST /api/chat/upload → fileId
    │ 2. 发送 BlockMessage JSON → POST /api/chat/stream/v3
    ▼
WebChatController
    │
    ▼
BlockMessageAssembler
    │ instanceof pattern matching 分发
    ├── TextBlock → 拼入文本
    ├── ImageBlock → fileId/base64 → Spring AI Media 对象
    └── FileBlock → Tika 提取文本 → 拼入上下文
    │
    ▼
SimpleChatService.chatStream(provider, UserMessage)
    │
    ▼
LLM (多模态: text + image Media)
    │ SSE Flux<String>
    ▼
前端渲染
```

## 2. Block 协议模型

### 2.1 类型体系

非 sealed 的开放接口，Jackson `@JsonTypeInfo` 按 `type` 字段分发。新增 block 类型只需：加 record + 加 `@JsonSubTypes` 条目 + 加 `instanceof` case。

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = TextBlock.class, name = "TEXT"),
    @Type(value = ImageBlock.class, name = "IMAGE"),
    @Type(value = FileBlock.class, name = "FILE"),
})
public interface MessageBlock {
    int id();
    String type();
}

public record TextBlock(int id, String text) implements MessageBlock {
    @Override public String type() { return "TEXT"; }
}

public record ImageBlock(int id, String fileId, String mimeType, String inlineData) implements MessageBlock {
    // fileId 和 inlineData 二选一
    // fileId: 已上传的图片引用
    // inlineData: <1MB 剪贴板图片的 base64 编码
    @Override public String type() { return "IMAGE"; }
}

public record FileBlock(int id, String fileId, String fileName, String mimeType, long size) implements MessageBlock {
    @Override public String type() { return "FILE"; }
}

public record BlockMessage(List<MessageBlock> blocks) {}
```

### 2.2 Block ID 规则

- 每个 block 有自增 `id`，从 1 开始，从上到下递增
- 不同类型的 block 独立存在，互不合并
- 前端序列化时按 DOM 顺序分配 id

### 2.3 文本拆分规则

- 一段连续文本（无 `\n`）→ 单个 TextBlock
- 含 `\n` 的文本 → 按 `\n` 拆分为多个 TextBlock，每个分配独立 id

### 2.4 Wire Format 示例

```json
{
  "blocks": [
    { "id": 1, "type": "TEXT", "text": "请分析下面的图片" },
    { "id": 2, "type": "IMAGE", "fileId": "a1b2c3", "mimeType": "image/png" },
    { "id": 3, "type": "TEXT", "text": "再结合这个文档" },
    { "id": 4, "type": "FILE", "fileId": "d4e5f6", "fileName": "report.pdf", "mimeType": "application/pdf", "size": 204800 },
    { "id": 5, "type": "TEXT", "text": "给出综合分析" }
  ]
}
```

## 3. 文件上传与存储

### 3.1 FileStorageService

- 存储目录：`${zechat.upload.dir}`（默认 `./uploads`）
- 命名：UUID fileId，按日期子目录 `yyyy/MM/dd/uuid.ext`
- 返回 `UploadedFileMeta` record

### 3.2 UploadedFileMeta

```java
public record UploadedFileMeta(
    String fileId,
    String fileName,
    String mimeType,
    long size,
    Instant uploadTime  // UTC，时区无关
) {}
```

### 3.3 限制

- 单文件最大 10MB（Spring multipart 配置）
- MIME 白名单：
  - 图片：`image/png`, `image/jpeg`, `image/gif`, `image/webp`
  - 文档：`application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.*`
  - 文本：`text/plain`, `text/markdown`, `text/csv`
- 超限 → `413 Payload Too Large`
- 不支持的类型 → `415 Unsupported Media Type`

### 3.4 无自动清理

当前无状态单轮模式，暂不实现文件清理。后续可加定时任务。

## 4. Block 解析管线

### 4.1 BlockMessageAssembler

核心组装器，用 `instanceof` pattern matching 处理各类型 block：

- **TextBlock** → 拼入文本 StringBuilder
- **ImageBlock** → 解析为 Spring AI `Media` 对象
  - `fileId` 非空 → 读本地文件 → `new Media(mimeType, resource)`
  - `inlineData` 非空 → base64 decode → `new Media(mimeType, byteArrayResource)`
- **FileBlock** → 调用 `FileContentExtractor` 提取文本 → 拼入上下文（格式：`[文件: filename]\n提取内容\n`）
- **未知类型** → log.warn + 跳过

最终输出：`new UserMessage(text, mediaList)`

### 4.2 文件内容提取（Strategy Pattern）

```java
public interface FileContentExtractor {
    boolean supports(String mimeType);
    String extract(Path path, String mimeType);
}
```

| 实现类 | 支持的 MIME | 方式 |
|--------|-----------|------|
| `PlainTextExtractor` | `text/*` | `Files.readString()` |
| `TikaExtractor` | PDF, Office, 兜底 | Apache Tika `AutoDetectParser` |

由 `CompositeExtractor` 遍历调度（遍历注入的 `List<FileContentExtractor>`，取第一个 `supports()` 返回 true 的）。

## 5. API 端点

### 5.1 新增端点

| Method | Path | Request | Response | Description |
|--------|------|---------|----------|-------------|
| `POST` | `/api/chat/upload` | `multipart/form-data` (file) | `UploadedFileMeta` JSON | 文件上传 |
| `GET` | `/api/chat/files/{fileId}` | - | 文件二进制流 | 文件回查（前端缩略图） |
| `POST` | `/api/chat/stream/v3` | `BlockMessage` JSON | SSE `Flux<String>` | Block 消息流式聊天 |

### 5.2 现有端点保留

`/api/chat` 和 `/api/chat/stream` 不变，向后兼容。

### 5.3 SimpleChatService 扩展

```java
// 新增重载
public Flux<String> chatStream(String provider, UserMessage userMessage)
```

内部使用 `.messages(userMessage)` 替代 `.user(string)`。

### 5.4 错误处理

| 场景 | HTTP 状态码 | 消息 |
|------|-----------|------|
| 文件超过 10MB | `413` | 文件过大，最大支持 10MB |
| 不支持的 MIME | `415` | 不支持的文件类型 |
| fileId 不存在 | `404` | 文件不存在 |
| Provider 不支持多模态但发了图片 | `400` | 当前模型不支持图片输入，请切换模型 |

## 6. 前端编辑器（chat3.html）

### 6.1 布局

```
┌──────────────────────────────────┐
│  zeChat                   [模型▼] │
├──────────────────────────────────┤
│                                  │
│  消息列表（user: 蓝色右对齐       │
│            assistant: 白色左对齐） │
│                                  │
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │
│ │  contenteditable div         │ │
│ │  (文字 + inline 图片缩略图)    │ │
│ └──────────────────────────────┘ │
│ [📎 附件]                  [发送] │
└──────────────────────────────────┘
```

### 6.2 图片输入（粘贴 + 拖拽）

1. 截获 `paste` / `drop` 事件，读取 `File` 对象
2. 判断大小：
   - **<1MB**：`FileReader` → base64 → 插入 `<img data-inline-data="..." data-mime-type="..." src="data:...">`
   - **≥1MB**：`POST /api/chat/upload` → 获取 `fileId` → 插入 `<img data-file-id="..." src="/api/chat/files/{fileId}">`
3. 编辑区内显示缩略图

### 6.3 文件上传

1. 点击附件按钮 → 触发 hidden `<input type="file">`
2. `POST /api/chat/upload` → 获取 `UploadedFileMeta`
3. 编辑区插入文件 chip：`<span class="file-chip" data-file-id="..." data-file-name="..." data-mime-type="..." data-size="...">📄 filename</span>`
4. chip 右上角带 × 删除按钮

### 6.4 序列化 serializeBlocks()

遍历 `contenteditable` 子节点，从上到下分配递增 id（从 1 开始）：

1. **文本节点** → 获取 `textContent`
   - 含 `\n` → split 后每段一个 `TextBlock`
   - 无 `\n` → 单个 `TextBlock`
   - 空字符串跳过
2. **`<img data-inline-data>`** → `ImageBlock(id, null, mimeType, inlineData)`
3. **`<img data-file-id>`** → `ImageBlock(id, fileId, mimeType, null)`
4. **`<span.file-chip>`** → `FileBlock(id, fileId, fileName, mimeType, size)`
5. **`<br>`** → 忽略，不生成 block（仅作为视觉换行，文本拆分由 `\n` 规则处理）
6. **`<div>` / `<p>`** → 递归处理子节点（contenteditable 换行产生的包装元素）

### 6.5 发送

```javascript
const blocks = serializeBlocks();
fetch('/api/chat/stream/v3', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ blocks })
});
// SSE 读取与现有 chat.html 逻辑相同
```

### 6.6 消息渲染

- **用户消息**：文本正常显示 + 图片显示缩略图 + 文件显示标签
- **AI 回复**：Markdown 渲染，SSE 流式追加（复用现有逻辑）

## 7. 依赖变更

`pom.xml` 新增：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
```

`application.yml` 新增：

```yaml
zechat:
  upload:
    dir: ./uploads
    max-size: 10MB
    allowed-types:
      - image/png
      - image/jpeg
      - image/gif
      - image/webp
      - application/pdf
      - application/msword
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.ms-excel
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
      - text/plain
      - text/markdown
      - text/csv

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

## 8. 文件清单

### 新建文件

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `module/webchat/model/block/MessageBlock.java` | Block 接口 + Jackson 分发 |
| 2 | `module/webchat/model/block/TextBlock.java` | 文本 block |
| 3 | `module/webchat/model/block/ImageBlock.java` | 图片 block |
| 4 | `module/webchat/model/block/FileBlock.java` | 文件 block |
| 5 | `module/webchat/model/block/BlockMessage.java` | 请求 DTO |
| 6 | `module/webchat/model/UploadedFileMeta.java` | 上传元数据 |
| 7 | `module/webchat/service/FileStorageService.java` | 文件存储服务 |
| 8 | `controller/FileController.java` | 上传/回查端点 |
| 9 | `module/aichat/block/BlockMessageAssembler.java` | Block → UserMessage 组装 |
| 10 | `module/aichat/block/extractor/FileContentExtractor.java` | 提取器接口 |
| 11 | `module/aichat/block/extractor/PlainTextExtractor.java` | 纯文本提取 |
| 12 | `module/aichat/block/extractor/TikaExtractor.java` | Tika 提取（PDF/Office/兜底） |
| 13 | `module/aichat/block/extractor/CompositeExtractor.java` | 组合调度 |
| 14 | `static/chat3.html` | 富文本编辑器前端 |

### 修改文件

| # | 文件路径 | 变更 |
|---|---------|------|
| 1 | `WebChatController.java` | 新增 `/stream/v3` 和 upload/files 端点 |
| 2 | `SimpleChatService.java` | 新增 `chatStream(provider, UserMessage)` 重载 |
| 3 | `application.yml` | 上传目录 + multipart + 白名单配置 |
| 4 | `pom.xml` | Tika 依赖 |

## 9. 不在范围内

- 多轮对话上下文（保持无状态单轮）
- 文件自动清理
- Provider 多模态能力自动检测（直接报错提示）
- Code block、Quote block 等扩展类型（接口已预留扩展能力）

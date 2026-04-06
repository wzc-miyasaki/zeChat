# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**zeChat** is a Spring Boot 3.4.0 application (Java 21)，一个集成多 AI 供应商的聊天应用。支持通过 OpenAI 兼容接口对接 DeepSeek、MiniMax 等大模型，提供同步和流式（SSE）两种对话模式。使用 Maven 构建，Lombok 简化代码。

## Build and Development Commands

All commands use Maven (`mvn`) or the Maven wrapper (`./mvnw`):

- **Build the project**: `./mvnw clean package`
- **Run the application**: `./mvnw spring-boot:run`
- **Run tests**: `./mvnw test`
- **Run a single test**: `./mvnw test -Dtest=TestClassName`
- **Clean build artifacts**: `./mvnw clean`
- **Build docker image**: `./mvnw spring-boot:build-image`

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Key Technologies and Dependencies

- **Spring Boot 3.4.0**: Base framework
- **Spring Web**: REST API support
- **Spring AI (OpenAI)**: AI model integration via OpenAI-compatible endpoints
- **Project Reactor**: Reactive streams for SSE streaming responses (`Flux<String>`)
- **Lombok**: Reduces boilerplate with `@Data`, `@RequiredArgsConstructor`, etc.
- **Spring Boot Test**: Testing framework with JUnit 5

## Project Structure

```
src/main/java/zec/ghibli/zechat/
├── ZeChatApplication.java                          # Spring Boot entry point
├── controller/
│   ├── OpenapiController.java                      # 直接调用各 AI 供应商的 REST 接口
│   └── WebChatController.java                      # Web 聊天主接口（同步 + 流式）
└── module/
    ├── aichat/                                     # AI 对话模块
    │   ├── advisor/
    │   │   └── StripThinkingAdvisor.java           # 过滤 <think> 标签的 Advisor
    │   ├── config/
    │   │   └── AiChatConfig.java                   # 多供应商 ChatClient 配置
    │   ├── prop/
    │   │   └── AiChatProp.java                     # AI 供应商配置属性类
    │   └── service/
    │       └── SimpleChatService.java              # AI 聊天服务（支持同步/流式）
    └── webchat/                                    # Web 聊天模块
        ├── model/
        │   └── ChatMessage.java                    # 聊天消息模型
        └── service/
            └── WebChatService.java                 # Echo 聊天服务（占位实现）
```

```
src/main/resources/
├── application.yml                                 # Spring 配置（AI 供应商、模型等）
└── static/
    └── chat.html                                   # 单页聊天前端（SSE 流式支持）
```

## Architecture

```
Web UI (chat.html)
        │ HTTP / SSE
        ▼
Controller Layer
  ├── WebChatController (/api/chat, /api/chat/stream)
  └── OpenapiController (/openapi/minimax, /openapi/deepseek)
        │
        ▼
Service Layer
  ├── WebChatService        → Echo 占位服务
  └── SimpleChatService     → AI 供应商调用（通过 ChatClient）
        │
        ▼
Config Layer
  ├── AiChatConfig          → 动态构建 Map<String, ChatClient>
  ├── AiChatProp            → 读取 ai.chat.providers.* 配置
  └── StripThinkingAdvisor  → 过滤模型返回的 <think> 块
        │
        ▼
External AI APIs (OpenAI-compatible)
  ├── DeepSeek  (https://api.deepseek.com)
  └── MiniMax   (https://api.minimaxi.com)
```

## Key Design Patterns

- **Multi-Provider Pattern**: 通过 `AiChatProp` 动态配置多个 AI 供应商，以 `Map<String, ChatClient>` 注入
- **Advisor Pattern**: `StripThinkingAdvisor` 实现 `BaseAdvisor`，拦截并过滤 AI 响应中的思考过程
- **Layered Architecture**: Controller → Service → Config → External API
- **Reactive Streaming**: 使用 `Flux<String>` + SSE 实现流式对话

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/chat` | 同步聊天（返回 ChatMessage） |
| POST | `/api/chat/stream` | 流式聊天（返回 SSE Flux） |
| POST | `/openapi/minimax` | 直接调用 MiniMax 模型 |
| POST | `/openapi/deepseek` | 直接调用 DeepSeek 模型（流式） |

## Configuration

- **Java version**: 21
- **Spring Boot version**: 3.4.0
- **Config file**: `application.yml`（已从 properties 迁移至 YAML）
- **AI providers**: 通过 `ai.chat.providers.*` 配置，每个 provider 包含 `apiKey`、`baseUrl`、`model`
- **Spring AI auto-config**: 已禁用（`spring.ai.openai.chat.enabled: false`），使用自定义多供应商配置

## Important Patterns and Conventions

- Use `@SpringBootApplication` on the main application class
- Lombok annotations (`@Data`, `@RequiredArgsConstructor`, etc.) should be used for model/entity classes
- AI provider configuration via `AiChatProp` + `AiChatConfig`, not Spring AI auto-configuration
- `StripThinkingAdvisor` should be attached to all ChatClient instances to handle thinking models
- API keys should be kept in environment variables, not in version control

## Testing

The project includes `spring-boot-starter-test` which provides JUnit 5, Mockito, and other testing utilities. Test class: `src/test/java/zec/ghibli/zechat/ZeChatApplicationTests.java`.

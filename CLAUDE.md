# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**zeChat** is a Spring Boot 3.4.0 application (Java 21) with WebSocket support, designed as a foundation for building a chat application with AI capabilities. It uses Maven for build management and Lombok for reducing boilerplate code.

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
- **Spring WebSocket**: Real-time bidirectional communication (already included for chat functionality)
- **Lombok**: Reduces boilerplate with `@Data`, `@RequiredArgsConstructor`, etc.
- **Spring Boot Test**: Testing framework with JUnit 5

### For Spring AI Integration

When adding Spring AI to this project (as planned), you'll need to:
1. Add `spring-ai-openai-spring-boot-starter` (or other AI provider) to `pom.xml`
2. Configure API keys in `application.properties` (e.g., `spring.ai.openai.api-key`)
3. Create service classes to interact with AI models via Spring AI's abstraction layer
4. Consider WebSocket integration for real-time AI responses in the chat interface

## Project Structure

```
src/main/java/zec/ghibli/zechat/
├── ZeChatApplication.java          # Spring Boot entry point
```

```
src/main/resources/
├── application.properties           # Spring configuration
├── static/                         # Static web assets (if needed)
```

## Configuration

- **Java version**: 21
- **Spring Boot version**: 3.4.0
- **Application name**: zeChat (configured in `application.properties`)
- **Annotation processing**: Lombok is configured in Maven compiler plugin

## Important Patterns and Conventions

- Use `@SpringBootApplication` on the main application class
- Lombok annotations (`@Data`, `@RequiredArgsConstructor`, etc.) should be used for model/entity classes
- WebSocket configuration can be added via `@Configuration` classes extending `WebSocketConfigurer`
- Spring AI components (ChatClient, etc.) should be autowired where needed for AI integrations

## Testing

The project includes `spring-boot-starter-test` which provides JUnit 5, Mockito, and other testing utilities. Test class structure is already defined in `src/test/java/zec/ghibli/zechat/ZeChatApplicationTests.java`.

## Notes

- The project is fresh with only initial commits; expect the structure to grow as features are added
- WebSocket support is already present in dependencies—use it for real-time chat features
- When integrating Spring AI, keep API keys and sensitive configuration in environment variables, not in version control

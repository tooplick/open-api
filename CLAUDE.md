# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AI Open Platform — a backend gateway that aggregates multiple LLM providers behind a single **OpenAI-compatible** API. Spring Boot 3 + MyBatis-Plus + MySQL 8. Currently backend MVP only; frontend (Vue3+Vite+TS) is a future phase.

## Commands

```bash
# Compile (fast sanity check)
mvn -q -DskipTests compile

# Full build + Spring Boot fat jar -> target/ai-open-platform-0.0.1-SNAPSHOT.jar
mvn -DskipTests clean package

# Run
mvn spring-boot:run
# or: java -jar target/ai-open-platform-0.0.1-SNAPSHOT.jar

# Initialize database (creates schema `ai_open_platform` + seed models)
mysql -u root -p < src/main/resources/db/schema.sql

# Single test (no tests exist yet; pattern for when they do)
mvn test -Dtest=ClassName#methodName
```

- DB connection is in `src/main/resources/application.yml` (`spring.datasource`). Override JWT signing key via env `AIOPEN_JWT_SECRET` (must be >= 32 bytes or startup throws).
- First startup auto-creates admin `admin / admin` via `DataInitializer` (seeding bypasses the register DTO's password validation).

## Build / environment gotchas

- **JDK 24 + Lombok**: JDK 23+ no longer runs annotation processors found on the classpath. The `maven-compiler-plugin` `annotationProcessorPaths` block in `pom.xml` (with Lombok pinned to 1.18.38) is **load-bearing** — removing it silently breaks every Lombok-generated getter/constructor. Java language level is 17.
- **Runtime needs MySQL**: the app connects at startup (`DataInitializer`). Without a local MySQL you can only verify `compile`/`package`, not a real run.

## Architecture — the big picture

### Two independent auth domains (most important thing to understand)
Requests split into two worlds with **separate authentication and separate response conventions**:

1. **Console API `/api/**`** — JWT auth. `AuthInterceptor` (registered in `WebConfig`, excludes `/api/auth/login|register`) parses the `Authorization: Bearer <jwt>` header and populates `UserContext` (a ThreadLocal). Responses are wrapped in `common.result.Result<T>`. Exceptions handled by `GlobalExceptionHandler` (`@RestControllerAdvice`).
2. **Relay API `/v1/**`** — API Key auth. `RelayAuthService` validates `Bearer sk-...` against the `api_key` table per request. Responses are **raw OpenAI-compatible JSON / passthrough of the upstream body**, NOT `Result`-wrapped. Errors are returned in OpenAI shape (`{"error":{...}}`) by **controller-local `@ExceptionHandler`s inside `RelayController`**, which deliberately take precedence over the global handler. `/v1/**` is excluded from `AuthInterceptor`.

There is **no Spring Security framework** — only `spring-security-crypto` for BCrypt. Role checks are imperative: `UserContext.requireAdmin()` called inside controller methods.

### Request forwarding (`modules/relay`)
`RelayService.relayChat` is the core path: authenticate → read raw body → parse `model`/`stream` → select channel → forward → bill → log.
- Forwarding uses the **JDK `java.net.http.HttpClient`** (webflux/WebClient was intentionally removed). Non-streaming buffers the byte body; streaming pipes the upstream `InputStream` to the servlet `response.getWriter()` line-by-line, flushing each line (reconstructs SSE `\n\n` framing).
- Target URL = normalized `channel.baseUrl` (trailing slashes stripped) + the **incoming request URI**, with the channel's upstream key injected as the Bearer token.
- Token usage is scraped from the response `usage` object (or from a streaming chunk that contains one). RelayExceptions are only thrown *before* the response is committed.

### Channel selection (`ChannelServiceImpl.selectChannelForModel`)
Filter enabled channels whose comma-separated `models` column contains the requested model (exact token match) → keep those at the **highest `priority`** → **weighted random** by `weight`.

### Billing / quota
Quota is an abstract "points" unit. `model.promptPrice`/`completionPrice` are **points-per-token** (BigDecimal). Cost = `ceil(prompt_tokens*promptPrice + completion_tokens*completionPrice)`. Seed models have price 0 (free). Pre-request check rejects when `quota > 0 && used >= quota` (coarse, no per-call estimate). On success, `used_quota` is incremented on both `user` and `api_key` via `setSql` SQL increments.

### Persistence conventions (MyBatis-Plus)
- Most entities extend `common.entity.BaseEntity`: `createTime`/`updateTime` auto-filled by `MyMetaObjectHandler`; `deleted` is `@TableLogic` (global logic-delete is configured in `application.yml`). IDs are DB `AUTO_INCREMENT` (`id-type: auto`).
- **`Log` is the deliberate exception** — standalone entity, no `BaseEntity`, no logic delete, insert-only `createTime` (logs are never soft-deleted; the `log` table has no `deleted` column).
- Mappers are found via `@MapperScan("com.aiopen.platform.**.mapper")` — scoped to `*.mapper` packages on purpose, because scanning broader would try to register service interfaces as mappers.
- Controllers paginate with MyBatis-Plus `Page` and convert to `PageResult.of(page)` for the response — never serialize `IPage` directly.
- Sensitive write-only fields (`user.password`, `channel.apiKey`) are `@JsonIgnore` so they're never returned.

### Module layout
`com.aiopen.platform.modules.<feature>` with `{entity, mapper, dto, service, service.impl, controller}`. Cross-cutting code lives in `common/` (Result, exceptions, BaseEntity), `config/` (MyBatis-Plus, JWT props, CORS+interceptors, crypto, data init), and `security/` (JwtUtil, UserContext, AuthInterceptor).

See `README.md` for the full endpoint table and a curl walkthrough (login → create channel → create key → call `/v1/chat/completions`).

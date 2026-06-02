# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AI Open Platform — a gateway that aggregates multiple LLM providers behind a single **OpenAI-compatible** API. Backend (`src/main/...`): Spring Boot 3 + MyBatis-Plus + MySQL 8. Frontend console (`frontend/`): Vue 3 + Vite + TypeScript. In **development** the two run separately (the Vite dev server proxies `/api`, `/v1`, `/anthropic` to the backend); for **containerized deployment** they collapse into one image — the Vue build is bundled into the backend jar and served by Spring Boot (see *Docker / deployment (single image)*). **There is no billing/quota** — the platform records token usage in the `log` table but never meters, charges, or limits by cost.

## Backend commands

```bash
# Compile (fast sanity check)
mvn -q -DskipTests compile

# Full build + Spring Boot fat jar -> target/ai-open-platform-0.0.1-SNAPSHOT.jar
mvn -DskipTests clean package

# Run (needs a reachable MySQL)
mvn spring-boot:run
# or: java -jar target/ai-open-platform-0.0.1-SNAPSHOT.jar

# (Re)create database schema `ai_open_platform` + admin account
mysql -u root -p < src/main/resources/db/schema.sql

# Single test (no tests exist yet; pattern for when they do)
mvn test -Dtest=ClassName#methodName
```

- DB connection is in `src/main/resources/application.yml` (`spring.datasource`). Override JWT signing key via env `AIOPEN_JWT_SECRET` (must be >= 32 bytes or startup throws).
- First startup auto-creates admin `admin / admin` via `DataInitializer` (constructs the user directly, bypassing the register DTO's validation).
- **`schema.sql` is `DROP TABLE` + `CREATE`** — re-running wipes all data, and there are no migration scripts. To evolve an existing DB, hand-write `ALTER`s.

## Build / environment gotchas

- **JDK 24 + Lombok**: JDK 23+ no longer runs annotation processors found on the classpath. The `maven-compiler-plugin` `annotationProcessorPaths` block in `pom.xml` (with Lombok pinned to 1.18.38) is **load-bearing** — removing it silently breaks every Lombok-generated getter/constructor. Java language level is 17.
- **Runtime needs MySQL**: the app connects at startup (`DataInitializer`). Without a local MySQL you can only verify `compile`/`package`, not a real run.

## Architecture — the big picture

### Two independent auth domains (most important thing to understand)
Requests split into two worlds with **separate authentication and separate response conventions**:

1. **Console API `/api/**`** — JWT auth. `AuthInterceptor` (registered in `WebConfig`, excludes `/api/auth/login|register`) parses the `Authorization: Bearer <jwt>` header and populates `UserContext` (a ThreadLocal). Responses are wrapped in `common.result.Result<T>`. Exceptions handled by `GlobalExceptionHandler` (`@RestControllerAdvice`).
2. **Relay API `/v1/**`** — API Key auth. `RelayAuthService` validates `Bearer sk-...` against the `api_key` table per request (existence, status, expiry — **no quota check**). Responses are **raw OpenAI-compatible JSON / passthrough**, NOT `Result`-wrapped. Errors are returned in OpenAI shape (`{"error":{...}}`) by **controller-local `@ExceptionHandler`s inside `RelayController`**, which deliberately take precedence over the global handler. `/v1/**` is excluded from `AuthInterceptor`.

There is **no Spring Security framework** — only `spring-security-crypto` for BCrypt. Role checks are imperative: `UserContext.requireAdmin()` called inside controller methods.

### No `model` table — models are derived from channels
Models are **not** maintained by hand. Each channel's `models` column (comma-separated) declares what it serves. The set of usable models is the de-duplicated union over **enabled** channels:
- `AbilityServiceImpl.distinctModels()` / `distinctModels(group)` aggregates it. `/v1/models` (relay; filtered by the key's group and optional model whitelist) and `/api/models` (console; all) both read from there.
- There is no `model` entity/table and no per-token pricing anywhere.

### Channel routing via the `ability` table (core mechanism)
Channel selection does **not** scan `channel.models` strings. It uses a derived **`ability`** table:
- Each row = `(group, model, channel_id, enabled, priority, weight)` — the Cartesian expansion of a channel's `group × models`. A unique index on `(group, model, channel_id)` and an index on `(group, model, enabled)` turn routing into an indexed equality lookup.
- **Sync**: `AbilityServiceImpl.rebuildForChannel(channel)` deletes the channel's rows then bulk-inserts the expansion (`enabled` follows `channel.status`). Channel create/update/status-change call `rebuildForChannel`; delete calls `deleteForChannel`. All of this runs inside `ChannelServiceImpl`'s `@Transactional` methods so channel + ability commit together.
- **Selection**: `AbilityServiceImpl.selectChannelId(group, model)` filters enabled rows → keeps the **highest `priority`** tier → **weighted-random** by `weight` → returns a channel id, which `ChannelServiceImpl.selectChannelForModel(group, model)` resolves to a `Channel`.

### Groups tie keys to channels
Both `api_key` and `channel` carry a `group`. A relay request routes with the **API Key's group**, so a key can only reach channels that declared the same group (`channel.group` is comma-separated, so a channel may belong to several groups). An API Key may also set a comma-separated `models` whitelist; when non-empty the requested model must be in it (else 403). This is how "certain keys can only use certain channels" works.

### Request forwarding (`modules/relay`) — adapter pattern
`RelayService.relayChat` is the core path: authenticate → read raw body, parse `model`/`stream` → model-whitelist check → `selectChannelForModel(group, model)` → pick adapter → convert + forward → record token log.
- **Adapter abstraction** (`modules/relay/adaptor`): `UpstreamAdaptor` has `buildRequestUrl`, `applyAuthHeaders`, `convertRequest` (OpenAI→upstream), `convertResponse` (upstream→OpenAI, non-streaming), and `convertStreamLine` (per-line, streaming). `AdaptorFactory.get(channel.type)` routes `anthropic`/`claude` → `ClaudeAdaptor`, everything else → `OpenAiAdaptor`.
- `OpenAiAdaptor` is essentially passthrough (only rewrites the `model` field; SSE forwarded verbatim; usage read from the standard `usage` object). `ClaudeAdaptor` translates between OpenAI `/chat/completions` and Anthropic `/v1/messages`: hoists `system`, forces `max_tokens`, maps `stop_reason`↔`finish_reason`, and converts the Claude event stream into `chat.completion.chunk` SSE using `StreamState` to carry cross-line state.
- Transport is the **JDK `java.net.http.HttpClient`** (WebClient was intentionally not used). Non-streaming buffers the body; streaming reads the upstream `InputStream` line-by-line, runs each line through the adapter, and writes/flushes to `response.getWriter()`.
- Before forwarding, `channel.modelMapping` (JSON) renames the request model to the upstream model name, and `channel.apiKey` may contain newline-separated multiple keys (one is chosen at random).
- Token usage is scraped from the response/stream and written to the `log` table (`prompt/completion/total_tokens`). **No quota is charged** and `log` has no quota column. RelayExceptions are only thrown *before* the response is committed.

### Persistence conventions (MyBatis-Plus)
- Most entities extend `common.entity.BaseEntity`: `createTime`/`updateTime` auto-filled by `MyMetaObjectHandler`; `deleted` is `@TableLogic` (global logic-delete configured in `application.yml` as `logic-delete-field: deleted`). IDs are DB `AUTO_INCREMENT` (`id-type: auto`).
- **`Log` and `Ability` are the deliberate exceptions** — neither extends `BaseEntity`, neither has logic delete, and their tables have no `deleted` column. The global `logic-delete-field` matches by field name, so an entity without a `deleted` property is simply unaffected (this is why those two tables are safe without the column).
- `group` and `model` are SQL reserved/awkward words: entities map them with backticks, e.g. `@TableField("`group`")` on `Channel`/`ApiKey`/`Ability`.
- Mappers are found via `@MapperScan("com.aiopen.platform.**.mapper")` — scoped to `*.mapper` packages on purpose (broader scanning would try to register service interfaces as mappers).
- Controllers paginate with MyBatis-Plus `Page` and convert to `PageResult.of(page)` — never serialize `IPage` directly.
- Sensitive write-only fields (`user.password`, `channel.apiKey`) are `@JsonIgnore` so they're never returned.

### Module layout
`com.aiopen.platform.modules.<feature>` with `{entity, mapper, dto, service, service.impl, controller}`. The `ability` module is the derived routing layer maintained by `channel` writes; `relay/adaptor` holds the provider adapters. Cross-cutting code lives in `common/` (Result, exceptions, BaseEntity), `config/` (MyBatis-Plus, JWT props, CORS+interceptors, crypto, data init), and `security/` (JwtUtil, UserContext, AuthInterceptor).

## Frontend (`frontend/`)

Vue 3 + Vite + TypeScript SPA — **Pinia** (state), **Vue Router**, **axios** (HTTP). Components are **hand-written by design; do NOT add a UI component library** (Element Plus / Ant Design Vue, etc.). This is a standing project decision — Pinia/axios/Vue Router are fine, but UI widgets (tables, modals, pagination, toasts) are built by hand in `src/components/`.

### Frontend commands
```bash
cd frontend
npm install
npm run dev        # dev server :5173; proxies /api and /v1 -> localhost:8321 (vite.config.ts)
npm run build      # runs `vue-tsc --noEmit` THEN `vite build` — typecheck is part of the build
npm run typecheck  # vue-tsc --noEmit only
```
No frontend test runner exists. The build fails on type errors **and** on unused locals/params (`noUnusedLocals`/`noUnusedParameters` are on in `tsconfig.json`). All API calls use relative paths because of the dev proxy.

### The axios layer mirrors the backend's two response conventions (most important)
`src/api/http.ts` is the linchpin. Its `get/post/put/del` helpers **unwrap the `Result<T>` envelope** and return `.data.data`, so `api/*.ts` modules and views receive plain typed data. Error handling tracks the backend's split (see "Two independent auth domains"):
- **HTTP 401** (missing/invalid JWT, emitted by `AuthInterceptor`) → `forceLogout()` clears the store and redirects to `/login`.
- **HTTP 200 with `body.code !== 200`** (business exceptions incl. `403` forbidden, from `GlobalExceptionHandler`) → rejected as an `ApiError` plus an error toast.

So a forbidden error is **not** an HTTP error — it is a 200 the interceptor must inspect by `code`.

### Global singletons, not provide/inject
`composables/useToast.ts` (`toast.success/error/info`) and `composables/useConfirm.ts` (`confirmDialog()` returning `Promise<boolean>`) are module-level reactive singletons callable from anywhere — including the axios interceptor. Their hosts (`ToastHost.vue`, `ConfirmHost.vue`) are mounted once in `App.vue`.

### Auth, routing, and an intentional import cycle
`stores/auth.ts` persists `token`+`user` to localStorage; the storage keys live in `utils/constants.ts`. That constants file exists specifically to break a module init-order hazard: `http.ts ↔ stores/auth.ts ↔ router/index.ts` form an import cycle that is safe **only because the cross-references run at call time, not at module load** — keep it that way. The router guard enforces login and `meta.admin`-gated routes; `AppLayout.vue` hides admin nav items. **Frontend role checks are convenience only — the backend re-checks every admin action.**

### Models page is read-only; channels/keys carry a group
`ModelsView` is **read-only** (there is no create/update/delete) — it lists the aggregated available models from `/api/models` (a `string[]`). The channel form includes `group` and a `type` dropdown (openai/anthropic/...); the API Key form includes `group` and an optional `models` whitelist. Because `channel.apiKey` is `@JsonIgnore` (never serialized back), the channel edit form opens with a blank key field and the admin must re-enter the upstream key on every save (`ChannelRequest.apiKey` is `@NotBlank`); the field accepts newline-separated multiple keys.

See `README.md` for an endpoint table and a curl walkthrough (note: parts of it predate the no-billing / channel-derived-models refactor and may be stale).

## Docker / deployment (single image)

Deployment is **front-back combined** (matching the `new-api` blueprint): one image runs the whole platform, MySQL is the only separate container. `docker compose up -d --build` starts two services — `app` and `mysql` — and the Vue UI plus `/api`, `/v1`, `/anthropic` are all served on **port 8321** (same origin, so no CORS in prod). Local dev is unchanged: `npm run dev` + the Vite proxy still runs the two halves separately; only the container build bundles them.

- **Three-stage `Dockerfile`**: (1) `node` builds the Vue app; (2) `maven` copies that `dist/` into `src/main/resources/static/` *before* `mvn package`, baking the SPA into the fat jar; (3) `eclipse-temurin:17-jre` runs it. Building with JDK 17 in the image sidesteps the JDK 24 Lombok problem entirely. The root `.dockerignore` lets `frontend/` source into the build context but excludes `frontend/node_modules` and `frontend/dist`.
- **Spring Boot serves the SPA** via `WebConfig.addResourceHandlers`: `/**` → `classpath:/static/` with a `PathResourceResolver` that falls back to `index.html` for unmatched paths **except** those starting `api/`, `v1/`, `anthropic/` (those return `null`, preserving real routing/404 instead of being handed HTML). This is what lets Vue's `createWebHistory` deep links survive a hard refresh — don't drop the guard, or unknown API paths start returning the SPA page.
- **Config is env-only, no source edits**: compose overrides `application.yml` through `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (Spring relaxed binding) to point at the `mysql` service, plus `AIOPEN_JWT_SECRET`. `application.yml` still targets `localhost` for non-Docker runs.
- **Schema bootstraps once**: `schema.sql` is bind-mounted into MySQL's `/docker-entrypoint-initdb.d/` and runs **only when the data volume is empty** (the app inserts the admin row but never creates tables, so tables must pre-exist). `app` gates on a MySQL `service_healthy` healthcheck. Since `schema.sql` is `DROP`+`CREATE`, re-initializing means dropping the volume: `docker compose down -v`.

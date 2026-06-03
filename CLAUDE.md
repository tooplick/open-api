# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AI Open Platform — a gateway that aggregates multiple LLM providers behind a single **OpenAI-compatible** API (plus an **Anthropic-compatible inbound** at `/anthropic/v1/messages`). Backend (`src/main/...`): Spring Boot 3 + MyBatis-Plus + MySQL 8. Frontend console (`frontend/`): Vue 3 + Vite + TypeScript. In **development** the two run separately (the Vite dev server proxies `/api`, `/v1`, `/anthropic` to the backend); for **containerized deployment** they collapse into one image — the Vue build is bundled into the backend jar and served by Spring Boot (see *Docker / deployment (single image)*). **There is no billing/quota** — the platform records token usage in the `log` table but never meters, charges, or limits by cost.

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
- First startup auto-creates admin `admin / admin` via `DataInitializer` (constructs the user directly, bypassing the register DTO's validation) with `mustChangePassword=1`, so the **first login is forced through a username+password change** (see *System settings & first-login bootstrap*). A second `CommandLineRunner`, `SettingInitializer` (`@Order(1)`), seeds default `system_setting` rows with a non-destructive `putIfAbsent`.
- **`schema.sql` is `DROP TABLE` + `CREATE`** — re-running wipes all data, and there are no migration scripts. To evolve an existing DB, hand-write `ALTER`s.

## Build / environment gotchas

- **JDK 24 + Lombok**: JDK 23+ no longer runs annotation processors found on the classpath. The `maven-compiler-plugin` `annotationProcessorPaths` block in `pom.xml` (with Lombok pinned to 1.18.38) is **load-bearing** — removing it silently breaks every Lombok-generated getter/constructor. Java language level is 17.
- **Runtime needs MySQL**: the app connects at startup (`DataInitializer`). Without a local MySQL you can only verify `compile`/`package`, not a real run.

## Architecture — the big picture

### Two independent auth domains (most important thing to understand)
Requests split into two worlds with **separate authentication and separate response conventions**:

1. **Console API `/api/**`** — JWT auth via **Spring Security** (stateless filter chain in `config/SecurityConfig`). `JwtAuthenticationFilter` parses the `Authorization: Bearer <jwt>` header, loads the user through `CustomUserDetailsService`, and stores an `AuthUser` principal in the `SecurityContext`. `/api/auth/login|register` and `/api/settings/public` are `permitAll`; the rest of `/api/**` requires authentication. Responses are wrapped in `common.result.Result<T>`. Business exceptions are handled by `GlobalExceptionHandler` (`@RestControllerAdvice`); auth failures (401) and role denials (403) are written as `Result`-shaped JSON by `SecurityConfig`'s `AuthenticationEntryPoint` / `AccessDeniedHandler`.
2. **Relay API `/v1/**`** — API Key auth. `RelayAuthService` validates `Bearer sk-...` against the `api_key` table per request (existence, status, expiry — **no quota check**). Responses are **raw OpenAI-compatible JSON / passthrough**, NOT `Result`-wrapped. Errors are returned in OpenAI shape (`{"error":{...}}`) by **controller-local `@ExceptionHandler`s inside `RelayController`**, which deliberately take precedence over the global handler. The Anthropic inbound mirrors this — `AnthropicController` has its own `@ExceptionHandler`s emitting **Anthropic-shaped** errors. `/v1/**` and `/anthropic/**` are `permitAll` in the security chain (and skipped by `JwtAuthenticationFilter.shouldNotFilter`), so they bypass JWT entirely and rely on their own API-Key auth (`/v1` reads `Authorization: Bearer sk-...`; `/anthropic` reads `x-api-key`, falling back to `Bearer`).

Auth is built on **Spring Security 6** (`spring-boot-starter-security`). **Authentication**: login (`AuthService`) calls `AuthenticationManager.authenticate(...)` → `DaoAuthenticationProvider` + `CustomUserDetailsService` + BCrypt `PasswordEncoder`, then issues a JWT; per-request auth is the stateless `JwtAuthenticationFilter`. **Authorization**: method-level `@PreAuthorize("hasRole('ADMIN')")` on controllers (`@EnableMethodSecurity`); the current user is injected with `@AuthenticationPrincipal AuthUser`. The DB `role` column stores lowercase `admin`/`user` (`security/Roles`), mapped to authorities `ROLE_ADMIN`/`ROLE_USER`. Resource-ownership checks that aren't role-based (a user touching only their own API keys) stay imperative as `BusinessException(FORBIDDEN)`. **Login lives in its own `AuthService`, not `UserService`** — calling `AuthenticationManager` from `UserServiceImpl` would form a cycle (`UserServiceImpl → AuthenticationManager → UserDetailsService → UserService`), which Spring Boot rejects by default.

### System settings & first-login bootstrap
- **`system_setting`** is a flat key-value table (`config_key`/`config_value`, all values stored as strings) fronted by `SystemSettingService` (typed `get`/`getBool` accessors; keys are `SettingKeys` constants). `SettingController` serves a **public subset** at `GET /api/settings/public` (site name/subtitle, login announcement, default key group, register toggles — read by the login page, hence `permitAll`) and the **full set** at `GET`/`PUT /api/settings` (admin-only; `SettingsVO` is a full-overwrite blob). `SettingInitializer` seeds defaults at startup without clobbering existing values.
- **Registration is settings-gated**: `UserServiceImpl.register` throws `REGISTER_DISABLED` unless both `register.enabled` and `register.password.enabled` are on (both default true). `register.email`/`register.github` are **placeholder** toggles — those flows aren't built, and there is **no register page in the console yet** (the `/api/auth/register` endpoint works for password signup; the UI only exposes the toggles on the settings page).
- **Forced first-login credential change**: the seeded admin carries `user.mustChangePassword=1`. `PUT /api/user/initial-credentials` (`AuthService.changeInitialCredentials`) is the only thing that clears it — it changes **username + password together** and **reissues the JWT**, because the username is baked into the token and `JwtAuthenticationFilter` reloads by username, so a rename invalidates the old token. It refuses unless `mustChangePassword==1` (can't be abused as a general rename). The console pins such users to a `/first-login` route via the router guard until it's cleared; ordinary self-service password change is `PUT /api/user/password`.

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
- Each call is recorded to the `log` table — token counts via the `Usage` helper and timing/status via `RelayMetrics`. Beyond `prompt/completion/total_tokens`, a row captures `type` (1 success / 2 fail), `upstreamModel`, `endpoint`, `is_stream`, `httpStatus`, `durationMs`, `ttfbMs`, `upstreamMs`, `ip`, `userAgent`, `requestId`, and a `content` summary/error string — this is the detailed-observability surface the `/api/logs` console reads. **No quota is charged** and `log` has no quota column. RelayExceptions are only thrown *before* the response is committed.

### Anthropic-compatible inbound (`/anthropic`) — not the same as `ClaudeAdaptor`
Two Anthropic code paths exist and run in **opposite directions** — don't conflate them:
- **`ClaudeAdaptor` (outbound)**: the client speaks **OpenAI** to `/v1/...`; the chosen channel is type `anthropic`/`claude`, so the adapter converts OpenAI→Anthropic to call the upstream's `/v1/messages` and back.
- **`relay/anthropic` (inbound)**: the client speaks **Anthropic** to `/anthropic/v1/messages` (point a Claude/Anthropic SDK's base URL at `.../anthropic`). `AnthropicRelayService` converts Anthropic→OpenAI and forwards to the selected channel **as if it were OpenAI** — always `POST {baseUrl}/v1/chat/completions` with `Bearer` auth, **bypassing `AdaptorFactory`** — then converts the OpenAI response/stream back to Anthropic via `AnthropicConverter` (+ `AnthropicStreamState`). It reuses the same `RelayAuthService` → group/whitelist → `selectChannelForModel` → logging path as `/v1`. `GET /anthropic/v1/models` returns the group's models in Anthropic list shape.

### Persistence conventions (MyBatis-Plus)
- Most entities extend `common.entity.BaseEntity`: `createTime`/`updateTime` auto-filled by `MyMetaObjectHandler`; `deleted` is `@TableLogic` (global logic-delete configured in `application.yml` as `logic-delete-field: deleted`). IDs are DB `AUTO_INCREMENT` (`id-type: auto`).
- **`Log`, `Ability`, and `SystemSetting` are the deliberate exceptions** — none extends `BaseEntity`, none has logic delete, and none of their tables has a `deleted` column (`Log` keeps an INSERT-fill `createTime`, `SystemSetting` an INSERT_UPDATE-fill `updateTime`, `Ability` no timestamps at all). The global `logic-delete-field` matches by field name, so an entity without a `deleted` property is simply unaffected (this is why those tables are safe without the column). Note `Log.stream` maps to column `is_stream` via `@TableField`.
- `group` and `model` are SQL reserved/awkward words: entities map them with backticks, e.g. `@TableField("`group`")` on `Channel`/`ApiKey`/`Ability`.
- Mappers are found via `@MapperScan("com.aiopen.platform.**.mapper")` — scoped to `*.mapper` packages on purpose (broader scanning would try to register service interfaces as mappers).
- Controllers paginate with MyBatis-Plus `Page` and convert to `PageResult.of(page)` — never serialize `IPage` directly.
- Sensitive write-only fields (`user.password`, `channel.apiKey`) are `@JsonIgnore` so they're never returned.

### Module layout
`com.aiopen.platform.modules.<feature>` with `{entity, mapper, dto, service, service.impl, controller}` — the features are `user`, `apikey`, `channel`, `ability`, `relay`, `log`, `setting`. The `ability` module is the derived routing layer maintained by `channel` writes; `relay/adaptor` holds the provider adapters and `relay/anthropic` the Anthropic inbound; `setting` is the key-value system-settings store. Cross-cutting code lives in `common/` (Result, exceptions, BaseEntity), `config/` (MyBatis-Plus, JWT props, Spring Security `SecurityConfig`, crypto, and the two startup `CommandLineRunner`s — `DataInitializer` for the admin row and `SettingInitializer` for default settings; the SPA resource handler is in `WebConfig`), and `security/` (`JwtUtil`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `AuthUser`, `Roles`).

## Frontend (`frontend/`)

Vue 3 + Vite + TypeScript SPA — **Pinia** (state), **Vue Router**, **axios** (HTTP). The UI is built on **shadcn-vue** (Reka UI primitives + **Tailwind v4**); components are generated into `src/components/ui/` and **owned/edited in-repo** (add more with `npx shadcn-vue@latest add <name>` — `components.json` is configured). Icons are **`@lucide/vue`**, forms use **vee-validate + zod**, toasts use **vue-sonner**. The `cn()` helper is in `src/lib/utils.ts`; the theme — the original "Lumina Nexus" steel-grey **light** palette ported onto shadcn CSS variables (`:root` + `@theme inline`) — lives in `src/styles/main.css`. *History: the console was originally hand-written with no UI library; it was migrated to shadcn-vue (spec: `docs/superpowers/specs/2026-06-03-frontend-shadcn-vue-rewrite-design.md`). Adding a different UI component library on top is still discouraged — extend by adding/owning shadcn components.*

### Frontend commands
```bash
cd frontend
npm install
npm run dev        # dev server :5173; proxies /api, /v1, /anthropic -> localhost:8321 (vite.config.ts)
npm run build      # runs `vue-tsc --noEmit` THEN `vite build` — typecheck is part of the build
npm run typecheck  # vue-tsc --noEmit only
```
No frontend test runner exists. The build fails on type errors **and** on unused locals/params (`noUnusedLocals`/`noUnusedParameters` are on in `tsconfig.json`). All API calls use relative paths because of the dev proxy.

### The axios layer mirrors the backend's two response conventions (most important)
`src/api/http.ts` is the linchpin. Its `get/post/put/del` helpers **unwrap the `Result<T>` envelope** and return `.data.data`, so `api/*.ts` modules and views receive plain typed data. Error handling tracks the backend's split (see "Two independent auth domains"):
- **HTTP 401** (missing/invalid JWT, emitted by `SecurityConfig`'s `AuthenticationEntryPoint`) → `forceLogout()` clears the store and redirects to `/login`.
- **HTTP 200 with `body.code !== 200`** (business exceptions from `GlobalExceptionHandler` — validation, not-found, or an ownership-`403` raised as a `BusinessException`) → rejected as an `ApiError` plus an error toast.
- **Real HTTP 403** (role denial from `@PreAuthorize`, written as `Result` JSON by the `AccessDeniedHandler`) → axios error branch shows a toast; status ≠ 401 so **no** logout.

So a 401 always logs out; a 403 — whether it arrives as a real HTTP 403 (role denial) or as a 200 + `code:403` (ownership denial) — just shows an error toast. Both carry a `Result` body, so `body.message` drives the toast.

### Toasts and confirms
Toasts use **vue-sonner**: `import { toast } from 'vue-sonner'` and call `toast.success/error/info` from anywhere — **including the axios interceptor** (sonner's `toast` is a global imperative function, so non-component contexts work). The `<Toaster/>` (shadcn `sonner` wrapper) is mounted once in `App.vue`. Confirmations use shadcn **`AlertDialog`** declaratively, per-view: each deleting view holds its own `showDelete` boolean + a `deleteTarget` ref and renders an `<AlertDialog v-model:open>`. There is **no** global toast/confirm singleton anymore (the old `useToast`/`useConfirm` composables + `ToastHost`/`ConfirmHost` were removed in the shadcn-vue migration).

### Auth, routing, and an intentional import cycle
`stores/auth.ts` persists `token`+`user` to localStorage; the storage keys live in `utils/constants.ts`. That constants file exists specifically to break a module init-order hazard: `http.ts ↔ stores/auth.ts ↔ router/index.ts` form an import cycle that is safe **only because the cross-references run at call time, not at module load** — keep it that way. The router guard enforces login and `meta.admin`-gated routes; `AppLayout.vue` hides admin nav items. **Frontend role checks are convenience only — the backend re-checks every admin action.**

### Models page is read-only; channels/keys carry a group
`ModelsView` is **read-only** (there is no create/update/delete) — it lists the aggregated available models from `/api/models` (a `string[]`). The channel form includes `group` (channel `type` is not exposed in the form — it defaults to `openai` on create and is preserved on edit); the API Key form includes `group` and an optional `models` whitelist. Because `channel.apiKey` is `@JsonIgnore` (never serialized back), the channel edit form opens with a blank key field and the admin must re-enter the upstream key on every save (`ChannelRequest.apiKey` is `@NotBlank`); the field accepts newline-separated multiple keys. The channel form also has a **fetch-models** helper (`POST /api/channels/fetch-models`) that calls the upstream's `/v1/models` so the admin can populate the `models` field instead of typing it. Two more views exist beyond the CRUD pages: an admin-only **`SettingsView`** (`/settings`, backed by `/api/settings`) and **`FirstLoginView`** (`/first-login`) — the router guard force-redirects any logged-in user whose `user.mustChangePassword===1` to `/first-login` and keeps them there until they submit new credentials.

Endpoint tables, the auth/response conventions, and curl/SDK walkthroughs live in **`api.md`**. `README.md` is now a high-level overview; other sibling docs: `install.md` (Docker + manual setup), `CONTRIBUTING.md` (workflow, commit + pre-commit checks, red-line constraints), `AGENTS.md` (short index for AI agents), `TODO.md` (roadmap).

## Docker / deployment (single image)

Deployment is **front-back combined** (matching the `new-api` blueprint): one image runs the whole platform, MySQL is the only separate container. `docker compose up -d --build` starts two services — `app` and `mysql` — and the Vue UI plus `/api`, `/v1`, `/anthropic` are all served on **port 8321** (same origin, so no CORS in prod). Local dev is unchanged: `npm run dev` + the Vite proxy still runs the two halves separately; only the container build bundles them.

- **Three-stage `Dockerfile`**: (1) `node` builds the Vue app; (2) `maven` copies that `dist/` into `src/main/resources/static/` *before* `mvn package`, baking the SPA into the fat jar; (3) `eclipse-temurin:17-jre` runs it. Building with JDK 17 in the image sidesteps the JDK 24 Lombok problem entirely. The root `.dockerignore` lets `frontend/` source into the build context but excludes `frontend/node_modules` and `frontend/dist`.
- **Spring Boot serves the SPA** via `WebConfig.addResourceHandlers`: `/**` → `classpath:/static/` with a `PathResourceResolver` that falls back to `index.html` for unmatched paths **except** those starting `api/`, `v1/`, `anthropic/` (those return `null`, preserving real routing/404 instead of being handed HTML). This is what lets Vue's `createWebHistory` deep links survive a hard refresh — don't drop that guard, or unknown API paths start returning the SPA page. The fallback also returns `null` when `static/index.html` is absent (e.g. running the backend alone, frontend not bundled), so a backend-only run 404s cleanly instead of throwing `FileNotFoundException` on every unmatched path — keep that existence check too.
- **Config is env-only, no source edits**: compose overrides `application.yml` through `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (Spring relaxed binding) to point at the `mysql` service, plus `AIOPEN_JWT_SECRET`. `application.yml` still targets `localhost` for non-Docker runs. The compose file is parameterized with `${VAR:-default}` for three knobs — `AIOPEN_JWT_SECRET`, `MYSQL_PASSWORD` (kept in sync across `MYSQL_ROOT_PASSWORD`, the healthcheck, and the datasource password), and `APP_PORT` (host port; the container always listens on 8321) — all documented in `.env.example`. Compose auto-reads a `.env` in the repo root, but **only `docker compose` consumes it — a local `mvn spring-boot:run` does not** (it reads `application.yml` / real env vars). `.env` is gitignored; `.env.example` is committed.
- **Schema bootstraps once**: `schema.sql` is bind-mounted into MySQL's `/docker-entrypoint-initdb.d/` and runs **only when the data volume is empty** (the app inserts the admin row but never creates tables, so tables must pre-exist). `app` gates on a MySQL `service_healthy` healthcheck. Since `schema.sql` is `DROP`+`CREATE`, re-initializing means dropping the volume: `docker compose down -v`.

`install.md` documents both the Docker and the manual (JDK/Maven/MySQL/Node) setup paths; `.vscode/` ships recommended extensions and a Spring Boot launch config.

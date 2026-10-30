# LinkHub

A **URL shortener** backend with Redis caching, Bloom filter, distributed locks, async click analytics, JWT auth, RabbitMQ mail, and an optional LangChain4j AI agent (with **Redis-backed chat memory**). Frontend lives in the sibling folder [`../link-hub-ui`](../link-hub-ui).

> **Disclaimer:** Demo / learning project. Not production-hardened (rate-limit fail-open, redirect allowlist, etc. still have known TODOs).

[中文完整说明](#中文)（英文在前，方便 GitHub 阅读）

---

## Features

- Short-link create / custom code / delete / list
- Fast redirect path: **Redis cache (with status)** → **Bloom filter** → **Redisson lock** → MySQL
- Admin ban/unban with **after-commit cache eviction** (avoids stale cache races)
- Async click stats (DB counter + Redis daily trend + click logs); `click_num` is `BIGINT` / `Long`
- Auth: email verification code (MQ), JWT **access + refresh** (refresh stored in Redis)
- Redirect IP rate limiting; Agent per-user rate limiting
- AI assistant (SSE): generate / query links with RAG FAQ + tool idempotency
- **Agent chat memory** persisted in Redis (`RedisChatMemoryStore`), keyed by user, TTL configurable
- API docs via Knife4j (dev)

---

## Tech stack

| Layer | Choice |
|--------|--------|
| Backend | Java **21**, Spring Boot **3.3.4**, Spring Security, MyBatis-Plus |
| Cache / lock / agent memory | Redis **7.4**, Redisson (lock + Bloom), `StringRedisTemplate` for chat history |
| MQ | RabbitMQ **3.13** (mail + DLX) |
| Auth | JWT (jjwt), BCrypt |
| AI | LangChain4j (DeepSeek chat + SiliconFlow embedding) |
| Docs | Knife4j / OpenAPI 3 |
| Frontend | Vue 3 + Vite + Element Plus — sibling **`link-hub-ui`** |
| Infra | Docker Compose (MySQL / Redis / RabbitMQ); optional app **Dockerfile** |

---

## Architecture (sketch)

```
Client / link-hub-ui
        │
        ▼
┌─────────────────┐   JWT      ┌──────────────┐
│ Controllers     │◄──────────►│ Security     │
│ /auth /link     │            │ + JWT filter │
│ /redirect /agent│            └──────────────┘
└────────┬────────┘
         │
         ▼
┌─────────────────┐   ┌──────────┐  ┌──────────┐  ┌──────────┐
│ Services        │──►│  Redis   │  │ RabbitMQ │  │  MySQL   │
│ ShortLink / Auth│   │ cache    │  │ mail+DLQ │  │ links    │
│ Bloom / Stats   │   │ Bloom    │  └──────────┘  └──────────┘
│ Agent tools     │   │ rate     │
│ Agent memory    │   │ chat mem │
└─────────────────┘   └──────────┘
```

Redirect hot path cares about **cache consistency with ban status** and **spoof-resistant client IP** for rate limits. Agent turns reuse prior messages via Redis when the same user chats again.

---

## Repository layout

```
link-hub/                 # this backend repo
├── docker-compose.yml    # MySQL / Redis / RabbitMQ
├── Dockerfile            # optional: package the fat jar into a JRE 21 image
├── sql/                  # schema / alter scripts
├── src/main/java/com/linkhub/
│   ├── agent/            # LangChain4j agent + RAG + memory
│   │   └── memory/       # RedisChatMemoryStore
│   ├── config/           # Security, Redis, MQ, rate limit, IP, AgentConfig
│   ├── controller/
│   ├── service/
│   ├── interceptor/
│   └── ...
└── src/main/resources/

../link-hub-ui/           # Vue3 admin UI (sibling folder, separate repo optional)
```

---

## Quick start

### Prerequisites

- JDK **21** (Lombok / tests fail on newer JDKs such as 25)
- Maven 3.9+ (or `./mvnw`)
- Docker + Docker Compose
- Node.js 18+ (for `link-hub-ui`)

### 1. Environment

Copy your local secrets into a root **`.env`** (gitignored). Names only — never commit real values:

| Variable | Purpose |
|----------|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password |
| `MYSQL_DATABASE` | Database name |
| `REDIS_PASSWORD` | Redis `requirepass` |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ credentials |
| `JWT_SECRET_KEY` | HMAC secret (≥ 32 bytes) |
| `AI_API_KEY` | DeepSeek (or compatible) chat key |
| `EMBEDDING_API_KEY` | SiliconFlow embedding key |
| `SPRING_PROFILES_ACTIVE` | e.g. `dev` |
| `HOST_IP` | Optional; Redis cluster demo only |

Also provide profile YAML (e.g. `application-dev.yml`, gitignored) for datasource / Redis / mail — use the `*.example` templates if present.

Optional app config (defaults are fine for local):

| Key | Default | Purpose |
|-----|---------|---------|
| `agent.chat-memory.ttl-days` | `7` | Redis TTL for Agent chat history |
| `client-ip.trust-forwarded-headers` | `false` | Only trust `X-Forwarded-For` behind a known proxy |

### 2. Start infrastructure

```bash
docker compose up -d redis mysql rabbitmq
```

| Service | Port (host) |
|---------|-------------|
| MySQL | `13001` → 3306 |
| Redis | `6379` |
| RabbitMQ AMQP | `25672` → 5672 |
| RabbitMQ UI | `15672` |

Apply schema / migrations under `sql/` as needed (including `sql/alter_click_num_bigint.sql` if upgrading).

### 3. Run backend

```bash
# Use JDK 21
java -version

./mvnw spring-boot:run
# or: mvn spring-boot:run
```

- API base: `http://localhost:18080`
- Knife4j: `http://localhost:18080/doc.html`

### 4. Run frontend (`link-hub-ui`)

```bash
cd ../link-hub-ui
npm install
npm run dev
```

Vite typically proxies API calls to `http://localhost:18080`.

### 5. Optional: Docker image for the app

The root **`Dockerfile`** does **not** compile the project. Typical flow:

```bash
mvn -DskipTests package
docker build -t link-hub .
docker run --rm -p 18080:18080 --env-file .env link-hub
```

- Base image: `eclipse-temurin:21-jre`
- Copies `target/*.jar` into `/app/app.jar`
- **`EXPOSE 18080`** (matches `server.port`; older drafts wrongly used 9527)

Day-to-day local work usually uses IDEA + Compose for deps only; the Dockerfile is for packaging the backend for a uniform runtime.

---

## Agent chat memory

| Item | Detail |
|------|--------|
| Class | `com.linkhub.agent.memory.RedisChatMemoryStore` |
| Wired in | `AgentConfig` → `MessageWindowChatMemory` + `ChatMemoryStore` |
| Redis key | `agent:chat:memory:{memoryId}` (memoryId ≈ user id) |
| Window | Last **20** messages in-process window; full list serialized to Redis |
| TTL | `agent.chat-memory.ttl-days` (default **7**), refreshed on each update |

Without Redis, the Store bean would fail at runtime; with Redis up, the same user can continue a chat after process restart (until TTL expires).

---

## API overview

| Area | Path | Notes |
|------|------|--------|
| Auth | `POST /auth/register`, `/code`, `/login`, `/refresh`, `/logout` | Public |
| Links | `POST /link/generate`, `DELETE /link/del`, `/dels`, `GET /link/list`, `/overview`, `/stats/{code}` | JWT |
| Admin | `PUT /link/admin/status` | `ROLE_ADMIN` |
| Redirect | `GET /redirect/{shortCode}` | Public, rate-limited |
| Agent | `POST /agent/chat` (SSE) | JWT, per-user rate limit |

Unified JSON envelope: `{ code, message, data }`.

---

## Security notes (high level)

- Stateless JWT with **access + refresh**; refresh tokens stored in Redis for logout/rotation
- Token identity anchor is the immutable `userId`: JWT `subject` = userId (`uname` claim is a display-only snapshot); refresh key is `refresh:user:{userId}`; the filter and `/auth/refresh` reload the user via `loadUserById`, so renaming does not break existing tokens. Login still authenticates by username + password.
- Changing the password or deleting the account removes `refresh:user:{userId}` after the transaction commits; issued access tokens stay valid until they expire (banned accounts are rejected by the filter immediately).
- Migration: tokens issued before this change (username subject, `refresh:{username}` key) are rejected; users must log in again once.
- Redirect rate limit by IP; Agent rate limit by user
- Client IP defaults to `remoteAddr` only (`client-ip.trust-forwarded-headers=false`) to reduce XFF spoofing
- Ban/delete: cache stores `status`; eviction runs **after transaction commit**
- Passwords hashed with BCrypt; secrets loaded from env / `.env`

This is **not** a claim of production security readiness.

---

## Tests

```bash
# Prefer JDK 21
mvn -q test
```

On machines where the default `java` is newer than 21, point `JAVA_HOME` at a JDK 21 install before running Maven.

---

## Screenshots

<!-- Optional: add UI / docs screenshots
![Dashboard](docs/images/dashboard.png)
-->

---

## License

No license file is attached yet. Treat as personal / academic code unless you add an SPDX license later.

---

## Author

Built as a full-stack learning project (short-link platform + AI agent).

---

## 中文

高性能**短链接平台**（Spring Boot 3 + Redis/MQ + Vue3 管理台 + LangChain4j Agent）。前端在同级目录 [`../link-hub-ui`](../link-hub-ui)。

> **说明：** 本项目为学习 / 演示用途，未按生产级安全做全面 hardening（限流降级、跳转白名单等仍有已知 TODO）。

### 功能特性

- 短链创建 / 自定义码 / 删除 / 列表
- 跳转热路径：**Redis 缓存（含 status）** → **布隆过滤器** → **Redisson 锁** → MySQL
- 管理端封禁 / 解禁，事务 **提交后再删缓存**（避免脏缓存回写）
- 异步点击统计（库计数 + Redis 日趋势 + 点击日志）；`click_num` 为 `BIGINT` / `Long`
- 认证：邮箱验证码（MQ）、JWT **access + refresh**（refresh 存 Redis）
- 跳转按 IP 限流；Agent 按用户限流
- AI 助手（SSE）：生成 / 查询短链，含 RAG FAQ 与工具幂等
- **Agent 对话记忆**持久化到 Redis（`RedisChatMemoryStore`），按用户隔离，TTL 可配
- 开发环境 Knife4j API 文档

### 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Java **21**、Spring Boot **3.3.4**、Spring Security、MyBatis-Plus |
| 缓存 / 锁 / Agent 记忆 | Redis **7.4**、Redisson（锁 + 布隆）、聊天历史用 `StringRedisTemplate` |
| 消息队列 | RabbitMQ **3.13**（邮件 + 死信） |
| 认证 | JWT（jjwt）、BCrypt |
| AI | LangChain4j（DeepSeek 对话 + SiliconFlow embedding） |
| 文档 | Knife4j / OpenAPI 3 |
| 前端 | Vue 3 + Vite + Element Plus — 同级 **`link-hub-ui`** |
| 基础设施 | Docker Compose（MySQL / Redis / RabbitMQ）；可选应用 **Dockerfile** |

### 架构示意

与上文英文 [Architecture (sketch)](#architecture-sketch) 相同：客户端 → Controller / Security → Service，再对接 Redis、RabbitMQ、MySQL。跳转路径关注**封禁状态与缓存一致**，以及限流用的**防伪造客户端 IP**；同一用户再次对话时，Agent 通过 Redis 续上历史消息。

### 仓库结构

与英文 [Repository layout](#repository-layout) 相同：后端在本仓库；`agent/memory` 下为 `RedisChatMemoryStore`；前端在同级 `../link-hub-ui`。

### 快速开始

#### 环境要求

- JDK **21**（JDK 25 等更新版本会导致 Lombok / 测试失败）
- Maven 3.9+（或 `./mvnw`）
- Docker + Docker Compose
- Node.js 18+（用于 `link-hub-ui`）

#### 1. 环境变量

将本地密钥写入根目录 **`.env`**（已 gitignore）。下表仅列**变量名**，切勿提交真实值：

| 变量 | 用途 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_DATABASE` | 数据库名 |
| `REDIS_PASSWORD` | Redis `requirepass` |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ 账号 |
| `JWT_SECRET_KEY` | HMAC 密钥（≥ 32 字节） |
| `AI_API_KEY` | DeepSeek（或兼容）对话密钥 |
| `EMBEDDING_API_KEY` | SiliconFlow embedding 密钥 |
| `SPRING_PROFILES_ACTIVE` | 如 `dev` |
| `HOST_IP` | 可选；仅 Redis 集群演示用 |

另需本地 profile YAML（如 `application-dev.yml`，已 gitignore）配置数据源 / Redis / 邮件——若有 `*.example` 模板可参照。

可选应用配置（本地默认即可）：

| 配置项 | 默认 | 用途 |
|--------|------|------|
| `agent.chat-memory.ttl-days` | `7` | Agent 对话历史 Redis TTL |
| `client-ip.trust-forwarded-headers` | `false` | 仅在可信反向代理后才信任 `X-Forwarded-For` |

#### 2. 启动基础设施

```bash
docker compose up -d redis mysql rabbitmq
```

| 服务 | 宿主机端口 |
|------|------------|
| MySQL | `13001` → 3306 |
| Redis | `6379` |
| RabbitMQ AMQP | `25672` → 5672 |
| RabbitMQ 管理台 | `15672` |

按需执行 `sql/` 下建表 / 变更脚本（升级时含 `sql/alter_click_num_bigint.sql`）。

#### 3. 启动后端

```bash
# 使用 JDK 21
java -version

./mvnw spring-boot:run
# 或: mvn spring-boot:run
```

- API：`http://localhost:18080`
- Knife4j：`http://localhost:18080/doc.html`

#### 4. 启动前端（`link-hub-ui`）

```bash
cd ../link-hub-ui
npm install
npm run dev
```

Vite 通常会把 API 代理到 `http://localhost:18080`。

#### 5. 可选：打应用 Docker 镜像

根目录 **`Dockerfile`** **不会**在镜像内编译项目。常见流程：

```bash
mvn -DskipTests package
docker build -t link-hub .
docker run --rm -p 18080:18080 --env-file .env link-hub
```

- 基础镜像：`eclipse-temurin:21-jre`
- 将本机 `target/*.jar` 复制为 `/app/app.jar`
- **`EXPOSE 18080`**（与 `server.port` 一致；旧稿误写过 9527）

日常本地开发多用 IDEA + Compose 只起依赖；Dockerfile 用于把后端打成统一运行时镜像。

### Agent 对话记忆

| 项 | 说明 |
|----|------|
| 类 | `com.linkhub.agent.memory.RedisChatMemoryStore` |
| 装配 | `AgentConfig` → `MessageWindowChatMemory` + `ChatMemoryStore` |
| Redis key | `agent:chat:memory:{memoryId}`（memoryId ≈ 用户 id） |
| 窗口 | 进程内最近 **20** 条；完整列表序列化进 Redis |
| TTL | `agent.chat-memory.ttl-days`（默认 **7**），每次更新刷新 |

Redis 未启动时 Store Bean 会在运行期失败；Redis 正常时，同一用户可在进程重启后继续对话（直至 TTL 过期）。

### API 概览

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `POST /auth/register`、`/code`、`/login`、`/refresh`、`/logout` | 公开 |
| 短链 | `POST /link/generate`，`DELETE /link/del`、`/dels`，`GET /link/list`、`/overview`、`/stats/{code}` | 需 JWT |
| 管理 | `PUT /link/admin/status` | `ROLE_ADMIN` |
| 跳转 | `GET /redirect/{shortCode}` | 公开，限流 |
| Agent | `POST /agent/chat`（SSE） | 需 JWT，按用户限流 |

统一响应：`{ code, message, data }`。

### 安全说明（概要）

- 无状态 JWT（**access + refresh**）；refresh 存 Redis，便于登出 / 轮换
- 身份锚点为不可变的 `userId`：JWT `subject` = userId（`uname` claim 仅作展示快照）；refresh 键为 `refresh:user:{userId}`；过滤器与 `/auth/refresh` 通过 `loadUserById` 回查用户，改用户名不会使已有 token 失效。登录仍按用户名 + 密码校验。
- 改密码或注销账号时，事务提交后删除 `refresh:user:{userId}`；已签发的 access 仍到期才失效（封禁账号由过滤器即时拒绝）。
- 迁移：本次改动前签发的 token（subject 为用户名、键为 `refresh:{username}`）会被拒绝，需重新登录一次。
- 跳转按 IP 限流；Agent 按用户限流
- 客户端 IP 默认只用 `remoteAddr`（`client-ip.trust-forwarded-headers=false`），降低 XFF 伪造
- 封禁 / 删除：缓存带 `status`；删缓存在**事务提交后**执行
- 密码 BCrypt；密钥来自环境变量 / `.env`

以上**不等于**已达生产安全标准。

### 测试

```bash
# 建议使用 JDK 21
mvn -q test
```

若本机默认 `java` 高于 21，请先将 `JAVA_HOME` 指到 JDK 21 再跑 Maven。

### 截图

<!-- 可选：可放管理台 / 文档截图
![Dashboard](docs/images/dashboard.png)
-->

### 许可证

暂未附带许可证文件。在添加 SPDX 许可证之前，请按个人 / 学业项目代码对待。

### 作者

全栈学习项目（短链平台 + AI Agent），用于实习求职展示。
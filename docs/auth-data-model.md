# Aspen 认证数据模型

> 文档状态：三层模型（端注册 / 端×登录方式 / 主体凭据）、4 表结构与 Principal SPI 契约已定稿；首批落地管理端密码登录全链闭环（图形/三方行为验证码闸门、刷新轮换、登出吊销、网关 JWT 收紧），第三方登录、设备与合作方端为预留扩展位；端注册与登录方式两表已随 2026-09-13 归属修订从 Admin sys 迁入本 Schema  
> 文档基线：2026-09-13  
> 关联文档：[技术架构](./technical-architecture.md)（14.2 节为认证架构权威）｜[Common 模块设计](./common-module-design.md)｜[Admin UPM 数据模型](./admin-upm-data-model.md)｜[Admin SYS 数据模型](./admin-sys-data-model.md)

## 1. 目标与来源

Aspen Auth 是平台级统一认证服务，负责登录协议编排、令牌签发与刷新、验证码闸门与登录审计；用户、凭证与权限的主体数据归各端用户域（管理端为 Admin UPM），Auth 经 Principal SPI 访问，自身不建立第二套用户数据。认证域四张表同库同 Schema（`aspen_auth`），代码位于：

```text
platform/aspen-auth/aspen-auth-api/src/main/kotlin/com/zax/aspen/auth/api/   # Principal SPI、登录端点契约与域枚举
platform/aspen-auth/aspen-auth-biz/src/main/kotlin/com/zax/aspen/auth/biz/   # 认证引擎、令牌、会话与验证码
platform/aspen-auth/aspen-auth-biz/src/main/resources/db/migration/          # V001 会话+审计, V002 端注册+登录方式
aspen-common/aspen-common-security/                                          # 业务进程最小信任链 (InternalTrustFilter)
```

设计修正了传统 `sys_oauth_client_details` 单表模式的结构性缺陷：那张表（Spring Security OAuth 2.x 遗物）把端机器凭据、登录策略与用户状态三类关注点压进一行，实际演进中必然膨胀为配置垃圾场。本模型拆为三层：

| 层 | 表 | 回答的问题 | 所在库 |
| --- | --- | --- | --- |
| 端注册 | `auth_client` | 谁在调用：端身份、机器密钥摘要、令牌 TTL | `aspen_auth`（迁移 V002） |
| 端×登录方式 | `auth_login_method` | 这个端允许怎么登录：认证方式、验证码闸门、密码策略 | `aspen_auth`（迁移 V002） |
| 主体凭据 | `upm_user_credential` 等 | 主体是谁、凭据对不对 | 各用户域（Auth 不持有） |

再加 Auth 自有的会话与审计层：`auth_session`（跨端刷新会话权威，V001）与 `auth_login_log`（登录审计，V001）。四表全部是平台级配置或 Auth 自有运行数据，全体租户共用，不做租户隔离；租户身份来自主体（用户所属租户），不来自端。

**归属演进说明（2026-09-13）**：两表最初随路由分发思路落在 Admin sys（Admin 权威 + Redis 版本信封分发），后修正——路由分发的必要性来自「消费者是不连数据库的网关」，而端配置的唯一消费者是 Auth 自身，跨库分发是为不存在的问题付出的复杂度。两表迁入 `aspen_auth` 后认证引擎本库直读，配置变更即时生效，快照分发链路整体退役；管理面经 `/admin-api/auth-client` 由本服务 controller/admin 承载（同任务服务管理面模式）。

认证引擎只有一条主干：**找主体（用户/设备/合作方）→ 验凭据 → 发令牌**。设备密钥、合作方客户端密钥与用户密码同构，均为「主体 + 静态凭据」的密码登录泛化。策略表存的是选择器（用哪种验证码、是否允许该方式、要不要强制改密），协议步骤本身是 Auth 内按方式枚举实现的代码，不做数据驱动。

## 2. 表清单

| 领域 | 表 | 职责 |
| --- | --- | --- |
| 端 | `auth_client` | 端注册：编码、类型、显示名、机器密钥摘要、访问/刷新令牌 TTL 覆盖、启停 |
| 端 | `auth_login_method` | 端×登录方式策略：允许的认证方式、验证码闸门、首登强制改密、密码有效期、方式专属配置 |
| 会话 | `auth_session` | 刷新会话权威：刷新令牌摘要（轮换覆盖）、端与主体、设备指纹、过期与吊销 |
| 审计 | `auth_login_log` | 登录审计：只追加的成功/失败记录（结果、失败类别、IP、UA） |

## 3. 表结构

### 3.1 `auth_client`

| 列 | 类型 | 语义 |
| --- | --- | --- |
| `client_id` | BIGINT UNSIGNED PK 自增 | 代理主键 |
| `client_code` | VARCHAR(64) UNIQUE NOT NULL | 端编码，小写中划线（`aspen-admin-web`），创建后不可修改 |
| `client_name` | VARCHAR(100) NOT NULL | 显示名，管理界面展示与搜索 |
| `client_kind` | VARCHAR(32) NOT NULL | 端类型枚举 `AuthClientKind`，签入令牌 `client_kind` claim |
| `secret_hash` | VARCHAR(100) NULL | 机器端密钥摘要（BCrypt）；仅 THIRD_PARTY 必填，第一方端不持密钥 |
| `hash_algorithm` | VARCHAR(32) NULL | 摘要算法标识，与 `upm_user_credential` 同约定 |
| `access_token_ttl_seconds` | INT UNSIGNED NULL | 访问令牌 TTL 覆盖；NULL 用全局默认（30 分钟） |
| `refresh_token_ttl_seconds` | INT UNSIGNED NULL | 刷新令牌 TTL 覆盖；NULL 用全局默认（7 天） |
| `status` | VARCHAR(32) NOT NULL | `EnabledStatus`；disabled 的端登录即时拒绝 |
| 审计列 | — | `MutableAuditEntity` 全套（`created_at/created_by/updated_at/updated_by/deleted_at/deleted_by`），逻辑删除同 `sys_route` |

### 3.2 `auth_login_method`

一行 = 一个端的一种登录方式，(client_id, method) 唯一。

| 列 | 类型 | 语义 |
| --- | --- | --- |
| `login_method_id` | BIGINT UNSIGNED PK 自增 | 代理主键 |
| `client_id` | BIGINT UNSIGNED NOT NULL | 所属端，普通外键 |
| `method` | VARCHAR(32) NOT NULL | 认证方式枚举 `AuthLoginMethodType` |
| `captcha_kind` | VARCHAR(32) NOT NULL | 验证码闸门 `CaptchaKind`；**方式级而非端级**（密码登录要验证码、第三方登录不需要） |
| `force_change_on_first_login` | TINYINT(1) NOT NULL DEFAULT 0 | 首次登录强制改密（密码系方式专用，其余方式恒 0） |
| `password_max_age_days` | INT UNSIGNED NULL | 密码有效期天数，过期登录触发强制改密（密码系方式专用） |
| `config` | JSON NULL | 方式专属配置（Jimmer `@Serialized`）；存引用别名（如身份提供方别名），**密钥本体一律不进此列** |
| `sort_order` | INT UNSIGNED NOT NULL DEFAULT 0 | 登录页展示顺序 |
| `status` | VARCHAR(32) NOT NULL | `EnabledStatus`；disabled 的方式行即时不可登录 |
| 审计列 | — | 同上，`MutableAuditEntity` |

### 3.3 `auth_session`（迁移 V001）

| 列 | 类型 | 语义 |
| --- | --- | --- |
| `session_id` | BIGINT UNSIGNED PK 自增 | 代理主键 |
| `client_kind` | VARCHAR(32) NOT NULL | 端类型，与令牌域对应 |
| `client_code` | VARCHAR(64) NOT NULL | 具体端编码 |
| `principal_id` | BIGINT UNSIGNED NOT NULL | 用户域内主体 ID（跨库不可外键，归属由 `client_kind` 判定） |
| `refresh_token_hash` | VARCHAR(100) NOT NULL | 当前有效刷新令牌摘要；**轮换即覆盖**，旧令牌立刻作废 |
| `device_id` | VARCHAR(64) NULL | 设备标识（首版取请求头/UA 摘要） |
| `ip` | VARCHAR(45) NULL | 登录来源 IP（IPv6 长度） |
| `user_agent` | VARCHAR(256) NULL | 登录 UA |
| `expires_at` | DATETIME(3) NOT NULL | 会话过期时间（= 登录时间 + 刷新 TTL） |
| `revoked_at` | DATETIME(3) NULL | 吊销时间；非空即不可刷新 |
| `revoked_reason` | VARCHAR(32) NULL | 吊销原因（LOGOUT/ROTATED/PRINCIPAL_DISABLED 等） |
| `created_at` / `updated_at` | DATETIME(3) | 无 by 列：行内主体即操作者，无独立操作人语义 |

索引：`refresh_token_hash` 唯一（刷新入口按哈希定位会话）、`(client_kind, principal_id)`（主体会话清单）。

### 3.4 `auth_login_log`（迁移 V001）

只追加、不修改、不删除。

| 列 | 类型 | 语义 |
| --- | --- | --- |
| `log_id` | BIGINT UNSIGNED PK 自增 | 代理主键 |
| `client_kind` / `client_code` | VARCHAR | 登录发生的端 |
| `principal_id` | BIGINT UNSIGNED NULL | 主体 ID；凭据未对上时可能为空 |
| `account` | VARCHAR(64) NULL | 密码登录尝试的账号（失败也记录，锁定排查用） |
| `method` | VARCHAR(32) NOT NULL | 认证方式 |
| `result` | VARCHAR(32) NOT NULL | `AuthLoginResult`：SUCCESS / FAILED_CREDENTIALS / FAILED_LOCKED / FAILED_CAPTCHA / FAILED_DISABLED / FAILED_METHOD |
| `failure_code` | VARCHAR(64) NULL | 细化失败码（预留，SPI 返回） |
| `ip` / `user_agent` | VARCHAR | 来源信息 |
| `created_at` | DATETIME(3) NOT NULL | 只增不改 |

索引：`(created_at)`、`(client_kind, principal_id, created_at)`。

## 4. 域枚举

全部实现 `AspenEnum`（code + description + color），位于 `aspen-auth-api` 的 `enums.auth`：

| 枚举 | 取值 | 说明 |
| --- | --- | --- |
| `AuthClientKind` | ADMIN / APP / DEVICE / THIRD_PARTY | 管理端 / 移动应用 / 设备端 / 第三方合作方；网关 claim↔前缀校验的依据 |
| `AuthLoginMethodType` | PASSWORD / DEVICE_SECRET / CLIENT_SECRET / THIRD_PARTY_WECHAT / THIRD_PARTY_APPLE | 静态凭据三件套（用户/设备/合作方）+ 第三方身份两件；SMS_OTP、小程序登录在准入清单外，出现真实需求再准入 |
| `CaptchaKind` | NONE / IMAGE / SLIDER | 无验证码 / 自研图形（预留实现）/ 三方行为验证码（拖动或点选，服务商凭据走环境变量；NONE 供本地开发降级） |
| `AuthLoginResult` | 见 3.4 | 登录结果分类（auth-biz 内部） |

## 5. 令牌与会话语义

- 访问令牌为 JWT，RS256 签名；私钥 PKCS#8 base64 只从环境变量注入（`ASPEN_AUTH_JWT_PRIVATE_KEY` + `ASPEN_AUTH_JWT_KEY_ID`，至少 4096 位），公钥经 Auth 的 `/internal/auth/jwks` 以 JWKS 分发，Gateway 用 `kid` 选钥，轮换期可同时发布多把公钥。
- claims：`iss=aspen-auth`、`sub=principalId`、`client_kind`、`client_code`、`tenant_id`（可空，C 端主体无租户）、`exp/iat/nbf`。权限摘要不进令牌，路由级 RBAC 由 Gateway 结合 `upm_permission_api` 判定（后续批次）。
- 刷新令牌为不透明随机值，摘要落 `auth_session`；刷新时经 SPI `getPrincipal` 复查主体状态（禁用即 401 并吊销会话），成功则轮换（旧哈希覆盖、发新对）。
- 登出吊销会话（`revoked_at`）；访问令牌不建黑名单，靠短 TTL 自然过期——这是明确的产品语义，不是遗漏。
- 管理端密码登录的强制改密：策略 × 状态（`upm_user.must_change_password` / `password_changed_at` + `upm_password_history`）共同判定，触发时返回改密动作而非完整能力令牌。

## 6. Principal SPI 契约

`aspen-auth-api` 的 `contract/auth/AuthPrincipalApi`（`/internal/auth/principal`，不经网关，服务身份保护），消费方为 `aspen-auth-biz`（自带 HTTP 客户端，UpmTenantApi 同款接线），实现方 v1 唯一：`aspen-admin-biz` UPM。

| 操作 | 请求 | 响应语义 |
| --- | --- | --- |
| `verifyPassword` | account + secret 明文 | OK（附主体）/ NOT_FOUND / BAD_CREDENTIALS / LOCKED / DISABLED；失败计数与锁定判定在用户域（`upm_user_credential.failed_attempt_count/locked_until`） |
| `resolveByIdentity` | identityType + identityId + allowCreate + profile | 返回主体；不存在且 allowCreate=false 时 NOT_FOUND；allowCreate=true 以身份唯一键幂等建号 |
| `getPrincipal` | principalId | 主体或不存在；刷新复查用 |

`UserPrincipalDto` 最小集：`principalId`、`displayName`、`status`（ENABLED/DISABLED/LOCKED）、`tenantId`（可空）、`mustChangePassword`、`passwordChangedAt`。SPI 是跨服务 HTTP 契约，破坏性变更升主版本；新增用户域（app 服务、设备注册表）通过新增实现 + Auth 路由项接入，不改引擎。

## 7. 配置读取与管理面

端注册与登录方式**本库直读，无分发介质**：认证引擎每次登录按 `client_kind` 查启用端及其方式行（行数个位数，直查即fresh，将来登录量级需要时再评估经 `AspenCacheOperations` 短 TTL 缓存——条目随首个真实消费者落地）。配置变更即时生效，不存在快照未加载窗口。

管理面（后续批次）：`aspen-auth-biz` 的 `controller/admin` 包承载端配置 CRUD（路径 `/admin-api/auth-client/...`，网关已有 `/admin-api/auth` 前缀路由覆盖），写路径经 Service 校验后本库落盘；Admin 控制台经网关访问，Admin 不持久化任何认证配置。行随实现落地的纪律不变：只保留有代码支撑的方式行，不预填。

## 8. 安全边界

- `auth_client.secret_hash`、`auth_session.refresh_token_hash` 只存不可逆摘要；明文密钥仅在创建/轮换时展示一次。
- JWT 私钥、微信/Apple 应用凭据、三方验证码凭据只从环境变量或密钥服务注入；`config` JSON 只存引用别名。种子数据、示例与测试不得包含可用凭据字面量；超管账号由启动 bootstrap 从 `ASPEN_UPM_BOOTSTRAP_ADMIN_PASSWORD` 创建。
- `auth_login_method` 行随实现落地：只种 v1 真实现的行（`aspen-admin-web` 端 + PASSWORD/SLIDER 方式），不预填没有代码支撑的方式。
- `auth_login_log` 只追加；会话数据不含凭据明文；跨端会话吊销是管理面能力（后续批次）。

## 9. Schema 管理

沿用纯 SQL 目录约定（无 Flyway），认证域四表全部落 `aspen-auth-biz` 的 `db/migration`：`V001__create_auth_schema.sql`（auth_session + auth_login_log）、`V002__create_auth_client.sql`（auth_client + auth_login_method，含管理端种子行）。`aspen_auth` 独立建库，部署侧手工创建（同 `aspen_admin`/`aspen_task` 约定）。Admin sys 的全局版本序列最高为 V006（认证服务路由种子；原 V006 两表迁移已随归属修订移除，因尚无环境执行过迁移、空号由路由种子递补）。UPM 既有 `upm_user_session`/`upm_login_log` 降级废弃（保留表结构不动，不再写入，见《Admin UPM 数据模型》）。

# Aspen Common 基础模块设计

> 文档状态：core/gen/database/cache/gateway/web/security 首版已实现；route（路由套件注解契约）随路由套件批次落地  
> 文档基线：2026-09-13  
> 关联文档：[技术架构](./technical-architecture.md)｜[认证数据模型](./auth-data-model.md)

## 1. 目的与边界

`aspen-common` 为 `services` 与 `platform` 下的微服务提供稳定、可选择的基础能力，不是一个包含所有依赖的公共工具包。第一版包含：

```text
aspen-common/
├── aspen-common-core/
├── aspen-common-gen/
├── aspen-common-database/
├── aspen-common-cache/
├── aspen-common-gateway-contract/
├── aspen-common-gateway/
├── aspen-common-route/
├── aspen-common-web/
└── aspen-common-security/
```

依赖方向只有：

```text
aspen-common-database -> aspen-common-core
aspen-common-cache    -> aspen-common-core
aspen-common-gen      -> aspen-common-core
aspen-common-gateway-contract -> (零项目依赖)
aspen-common-gateway  -> aspen-common-cache, aspen-common-gateway-contract
aspen-common-route    -> (零项目依赖, 编译类路径仅 Spring Web 注解 API)
aspen-common-web      -> aspen-common-core, aspen-common-route,
                         aspen-common-security (编译期可选, 限流主体读取)
aspen-common-security -> aspen-common-core, aspen-common-database
```

`core` 不依赖 Spring、Web、Jackson、Jimmer 或 Redis；`database` 与 `cache` 不互相依赖；`gateway-contract` 与 `core` 同级，同样零基础设施依赖，因此可被 `api` 安全引用；`route` 是同等定位的纯注解契约模块，只依赖 Spring Web 注解 API，供 `api` 契约接口声明端点（§8.3）；`gateway` 只依赖 `cache` 取 Redis 分发原语并依赖 `gateway-contract` 承载纯契约，不依赖 `database`；`web` 承载 MVC 运行约定（受众路径前缀、错误契约、Trace ID）与路由套件消费端（限流拦截器、操作日志拦截器、springdoc 定制），依赖 `core` 的错误契约类型、`route` 的注解定义，并以编译期可选方式引用 `security` 的身份上下文（运行期无 `security` 的服务自动回退，见 §8.3）；所有 common 模块禁止依赖任何服务的 `api` 或 `biz`。不存在 `common-all`，没有数据库或缓存需求的服务不引入对应模块。

源码注释遵守项目统一规范：注释正文使用中文，专有名称保留原文，标点使用英文字符，句尾不加句号。类、接口、枚举、对象、字段和方法必须有说明职责或约束的有效 KDoc，重要实现边界补充行注释。方法 KDoc 必须采用完整块格式（概述段 + 空行 + 每个参数的 `@param` + 非 `Unit` 返回的 `@return`），禁止只有单行概述的方法注释；该规则由根模块 KDoc 纪律测试强制。数据库实体和字段的 KDoc 必须详尽：类级注释说明职责与典型使用场景，字段有具体使用场景、取值约定、生命周期或对其他流程的影响时必须逐一写清楚，仅列名自解释且无附加语义的简单字段可不写字段注释。Jimmer 实体列名与属性名蛇形一致时不声明 `@Column`，由 Jimmer 自动解析，该规则由架构测试强制检查。完整规则和示例见《技术架构》7.8 节。

## 2. 与 pjcloud-common 的取舍

参考项目 `/Users/dengzhangchen/Desktop/work/zax/hq-platform/biz-api-cloud-zax/pjcloud-common` 用于识别已有能力，不复制源代码或业务常量。

| 参考项目做法 | Aspen 决策 | 原因 |
| --- | --- | --- |
| core 同时包含 `R<T>`、Web、Jackson、Swagger、异步配置和大量工具 | core 只保留错误、异常、分页和纯数据校验 | API 契约不能被整个运行时传递依赖污染 |
| data 同时包含 MyBatis、Redis、租户、数据权限、Feign 和 UPM 依赖 | database/cache 完全拆开 | 防止基础模块反向依赖具体业务和形成全家桶 |
| datasource 提供 Druid/MyBatis 动态数据源 | 首版一个服务一个 Schema/数据库账号 | 数据所有权明确，事务和租户隔离更容易审计 |
| Redis TTL 编码在 Cache 名称中 | 每个 Cache 通过属性显式声明 TTL | 配置可校验、可审查，不依赖名称解析约定 |
| Redis 自动拼租户 ThreadLocal | 首版无租户上下文和自动租户前缀 | 租户隔离语义尚未确定，不能隐式影响 SQL/Key |
| Redis Pub/Sub 承担跨实例通知 | 可靠事件使用 RocketMQ | Redis Pub/Sub 不提供可靠投递与失败恢复 |

不迁移 MyBatis/MyBatis-Plus、Druid、动态数据源、数据权限 SQL 注入、业务枚举、服务名常量、静态 Spring Context、Seata、Job 或通用 CRUD Service。

## 3. Core

包根为 `com.zax.aspen.common.core`。

| 类型 | 用途 |
| --- | --- |
| `ErrorCode` | 稳定机器错误码与默认安全消息，不包含 HTTP 状态 |
| `CommonErrorCode` | 参数、资源不存在、状态冲突、依赖不可用和内部错误 |
| `BusinessException` | Service 层错误载体，保留错误码、安全 detail 和 cause |
| `PageQuery` | 从 1 开始的框架无关分页请求，并安全计算 Long offset |
| `PageResult<T>` | 框架无关分页结果，不暴露 Spring Data 或 Jimmer 类型 |
| `Validation` | 不依赖 Bean Validation 的纯值校验函数 |
| `AspenEnum` | 业务枚举统一契约：`code` 为唯一持久化与契约值，`description` 为默认中文描述，`color` 为可空标签颜色；枚举保持纯净不标注 Jimmer 注解 |
| `EnumColor` | 标签颜色取值约定：5 个语义色与 11 个调色板色令牌，允许 `#RRGGBB` 逃生口，与 `sys_dict_item.color` 共用 |
| `AspenEnums` | 按 `code` 反查工具：`codeOf` 未命中返回 null，`requireOf` 未命中抛非法参数异常 |
| `enums.common.*` | 跨服务通用枚举子包：`Gender`（ISO/IEC 5218）、`EnabledStatus`、`SortDirection`（SQL 排序关键字）、`RiskLevel`（CVSS 严重度等级）；纳入条件与不纳入清单见《技术架构》12.4 节 |
| `GenDict` | 枚举字典镜像声明注解（`gen` 包）：code/name/group 对应 `sys_dict` 的 `dict_code`/`dict_name`/`dict_group`, 仅标注 `AspenEnum` 枚举, 框架无关 |
| `GenDictDescriptor` / `GenDictItemDescriptor` | 枚举字典扫描产物与跨服务上报模型（`gen` 包）, 构造时校验编码、分组格式与项值唯一, 可直接进入 HTTP 契约 |

业务错误码仍归拥有者 `*-api/error/{group}`。成功 HTTP 响应直接返回 DTO/VO；`aspen-common-web` 已把 `BusinessException` 统一转换为 RFC 9457 Problem Details 并保留正确 HTTP 状态码（§8），不提供 `R<T>` 或 `ApiResponse<T>` 包装。

错误契约中的机器错误码保持稳定英文，例如 `COMMON.INVALID_ARGUMENT`；默认错误消息和允许返回给调用方的 `BusinessException.detail` 统一使用中文。Java 类型、Cache 名称、数据库结构、下游地址和原始异常消息不得进入对外 `detail`，只允许写入受控日志或保存在异常 `cause` 中。

API 只有实际使用上述公共类型时才声明：

```kotlin
dependencies {
    api(project(AspenProjects.COMMON_CORE))
}
```

## 4. Database

包根为 `com.zax.aspen.common.database`。Jimmer 是唯一 ORM，公共模块提供：

- `CreateAuditEntity`：`created_at`（默认当前时间）与 `created_by` 创建审计。
- `UpdateAuditEntity`：`updated_at` 与 `updated_by` 更新审计。
- `DeleteAuditEntity`：`deleted_at` 时间戳逻辑删除与 `deleted_by` 删除人审计。
- `VersionedEntity`：Jimmer `@Version` 乐观锁字段，初始版本为 `1`。
- `LogicalDeletedEntity`：Jimmer `@LogicalDeleted` 布尔标记，另一种轻量删除风格。
- `TenantScopedEntity`：租户标识列映射，继承即声明为租户隔离表。
- `TenantFilter` / `TenantDraftInterceptor`：为全部租户实体查询自动追加租户条件、保存自动填充租户标识；缺失上下文一律拒绝（fail-closed），服务必须装配 `TenantContextSupplier` 提供。
- `AuditableEntity`：创建与更新审计的组合。
- `MutableAuditEntity`：完整审计、时间戳逻辑删除与乐观锁的组合。
- `AuditDraftInterceptor`：新增时写入创建/更新时间，更新时只改更新时间。
- `AspenEnumProviders`：把实现 `AspenEnum` 的枚举按 `code` 与数据库小写字符串互转，按 `aspen.database.enums.base-packages`（默认 `com.zax.aspen`）自动扫描注册，未知存储值拒绝转换。
- `DatabaseLimits`：统一校验分页和批处理上限。

原子映射按实体需要自由组合，命名组合只为高频形态提供捷径，不提供携带业务字段或表名的巨型 BaseEntity；`remark` 等展示性字段和业务性 JSON 数据由具体表按语义命名专用列，公共映射不提供通用 `extension` 兜底列。操作人审计列（`created_by`/`updated_by`/`deleted_by`）统一为字符串主体标识，各服务把用户 ID 或服务身份转为字符串写入，公共映射不假设主体 ID 的具体格式。`tenantId`、表名、业务字段和 Repository 仍属于具体 `biz`。common-database 不提供动态数据源、跨服务 Entity、BaseRepository、MySQL 驱动或数据库迁移执行器；多租户隔离只提供 `TenantScopedEntity` 列映射与 `TenantFilter` 自动过滤，租户上下文的获取与装配仍由各服务负责。

默认配置：

```yaml
aspen:
  database:
    audit:
      enabled: true
    pagination:
      default-size: 20
      max-size: 200
    batch:
      default-size: 100
      max-size: 1000
```

包含 Entity 的业务模块必须显式启用 KSP，并选择数据库驱动：

```kotlin
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(AspenProjects.COMMON_DATABASE))
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)
}
```

除 Spring Boot BOM 版本约束外，common-database 只通过 Gradle `api` 暴露 `jimmer-sql-kotlin`、core 和编译所需注解。Jimmer Spring Boot Starter 保持为 `implementation` 运行实现，避免业务模块的编译 API 被整个 Starter 扩大。Jimmer `0.11.7` 的 KSP 代码引用 `com.fasterxml.jackson.annotation`，Jackson 3 databind 也以该注解包作为兼容 API；common-database 因此传递 `jackson-annotations`，但不引入 Jackson 2 databind。

SQL 日志、Dialect、Schema 验证和 JDBC 超时继续使用 `jimmer.*` 原生属性。生产环境保持 `jimmer.inline-sql-variables=false`，禁止在日志中展开敏感 SQL 参数。

## 5. Cache

包根为 `com.zax.aspen.common.cache`。公共缓存模块提供严格 Key、显式 TTL、受控 JSON 序列化、`RedisCacheManager`、`AspenCacheOperations` 和非缓存语义的 `AspenRedisOperations` 分发原语。业务代码不直接操作 `RedisTemplate`，也不自行声明 Redis 客户端依赖；服务内的 `cache/redis` 封装组合这些基础操作并决定回源和失败策略。

```yaml
aspen:
  cache:
    enabled: true
    prefix: aspen
    environment: prod
    service: aspen-admin
    allowed-groups:
      - upm
      - sys
    allow-undeclared-caches: false
    max-entry-size: 1MB
    definitions:
      upm-user:
        group: upm
        domain: user
        ttl: 30m
```

Key 格式固定为：

```text
aspen:{environment}:{service}:{group}:{domain}:{identifier...}
```

空白、冒号、控制字符和非法 Key 段会被拒绝。两个 Cache 不能使用相同的 `group/domain`，避免形成不可见的数据碰撞。`allowed-groups` 是当前服务可选的业务组白名单，配置后同时约束 Spring Cache 定义和显式 Redis Key。Admin 接入缓存时必须配置为 `upm/sys`，该规则通过通用白名单实现，不把 Admin 服务名硬编码到 common 模块。

每个已声明 Cache 必须配置正数 TTL。默认禁止运行时创建未声明 Cache；确需临时放开时必须同时配置正数 `default-ttl`，未声明 Cache 会进入 `unclassified` 命名空间。该开关只用于受控过渡，不作为生产默认值。

序列化规则：

- 使用 Jackson 3 JSON，不使用 Java 原生序列化。
- 独立序列化配置不修改 Web ObjectMapper。
- 只允许 Aspen 类型、时间类型、受控集合和数组反序列化。
- 默认不缓存 `null`。
- 默认拒绝超过 1MB 的单值。
- Redis 或序列化失败通过 `CacheAccessException` 向上报告，不伪造命中或写入成功。

Spring Cache 的 `RedisCacheManager` 会自动创建，但 common-cache 不启用 `@EnableCaching`。使用注解缓存的服务必须在自身配置中显式开启。需要明确失败路径的业务代码优先使用：

```kotlin
val key = AdminCacheKeys.user(userId)
cacheOperations.put("upm-user", key, userView)
val cached = cacheOperations.get<UserView>("upm-user", key)
```

服务内键工厂是 `CacheKey` 的唯一合法构造入口。每个服务 biz 的 `cache/` 包提供唯一工厂对象 `<Service>CacheKeys`（Admin 为 `AdminCacheKeys`），规则如下：

- 调用点只能调用工厂函数构造 `CacheKey`，禁止手写 `CacheKey(...)`、group/domain 字面量和业务 ID 到 Key 段的 `toString()` 拼接；转换逻辑收在工厂内。
- 工厂函数与缓存 domain 一一对应，参数只接收业务标识（如 `userId: Long`）；复合键的段顺序属于调用契约，由工厂函数签名固化，调用方不得自行调换。
- 条目不预置：新工厂函数与配置中心对应 Cache 声明的 `group`/`domain`/`ttl`、第一个真实消费方同时落地，禁止为将来可能缓存的对象预留条目。
- 工厂与配置声明中的 group/domain 属于双处声明，运行时由 `CacheSettings.definition` 的一致性校验兜底；仓库内由架构测试模块 `aspen-architecture-test` 的中央边界测试 `CacheKeyFactoryBoundaryTest` 强制工厂之外不出现裸构造，键工厂文件必须位于服务 `cache/` 包。

`AspenCacheOperations` 第一版提供以下受控操作：

| 操作 | 语义 |
| --- | --- |
| `get` | 读取并校验调用方声明的类型 |
| `put` | 使用 Cache 定义中的 TTL 写入 |
| `putIfAbsent` | 使用 Redis `NX` 语义原子写入，返回是否创建成功 |
| `putIfPresent` | 使用 Redis `XX` 语义原子覆盖，成功时重置为定义的 TTL |
| `getAndEvict` | 使用 Redis `GETDEL` 语义原子读取并删除，适合一次性短期数据 |
| `contains` | 只检查 Key 是否存在，不读取缓存值 |
| `evict` | 删除一个完整命名空间下的确定 Key |

这些方法只接收 `CacheKey`，不能传入绕过项目、环境、服务和业务组命名空间的原始 Redis Key。写操作不接受临时秒数或永久保存开关，TTL 只能来自 `aspen.cache.definitions`。`contains` 只返回查询瞬间的状态，竞争流程必须直接使用 `putIfAbsent`，不能拼接“先检查再写入”。`putIfAbsent` 只提供条件缓存写入，不具备所有者令牌、续租或 fencing token，禁止把它直接当作分布式锁或可靠业务幂等。

`getAndEvict` 依赖 Redis `6.2` 引入的 `GETDEL` 命令，因此部署版本必须为 Redis `6.2+`，并在 Docker 镜像清单中固定经过验证的补丁版本。

参考 Redis 工具封装中的以下能力不进入公共缓存接口：

- 不提供 `flushdb`、`clear`、通配符删除或业务链路 `SCAN`，避免误删共享数据和阻塞 Redis。
- 不提供 `persist` 或没有 TTL 的批量写入，避免遗留无法治理的永久 Key。
- 不提供无边界 `hGetAll`、`sMembers`、`lRange(0, -1)` 等集合读取，真实集合场景必须先确定容量上限和游标协议。
- 不把 Redis List 封装成业务消息队列，可靠异步任务和事件继续使用 RocketMQ。
- 不在缓存工具中混入锁和信号量；需要时建立独立能力并明确所有者、租约、续期、fencing token 和故障恢复语义。
- 不为每条 Redis 命令建立同名薄包装；Hash、Set 和 Sorted Set 在出现明确业务模型后提供有界的领域封装。

缓存 TTL 和命名空间在应用启动时绑定。Nacos 修改后通过滚动重启生效，第一版不动态替换运行中的 CacheManager，避免同一集群实例使用不同 TTL。

`AspenRedisOperations` 提供非缓存语义的受控分发原语，与 `AspenCacheOperations` 并列：

| 操作 | 语义 | 典型用途 |
| --- | --- | --- |
| `increment(key)` | INCR 原子自增并返回新值 | 单调递增版本号 |
| `getValue(key)` | 读取永久键的字符串值 | 读取权威数据的分发快照 |
| `setValue(key, value)` | 无 TTL 整键原子替换（一条 SET，无删除窗口） | 发布全量版本化快照 |
| `setValueIfNewer(key, value, channel)` | 版本守卫发布（Lua CAS）：解析在途信封的 `version` 字段，仅当新值版本更大才 SET 并 PUBLISH，返回是否采纳 | 并发发布下保证「旧盖新」不可能发生 |
| `publish(channel, message)` | 发布通知消息 | 变更到达通知 |
| `subscribe(channel, handler)` | 注册字符串消息处理器（内部共享订阅容器） | 消费变更通知 |

使用边界：

- 只用于「缓存语义不适用」的场景：权威数据在数据库、Redis 只是可随时全量重建的分发介质，或单调计数与轻量变更通知；每个使用场景必须在本节登记，当前已登记场景为网关路由快照分发（Admin 发布、Gateway 只读消费，Key 与频道由 `aspen-common-gateway-contract` 的 `GatewayRouteContract` 定义）。曾有第二个登记场景「认证客户端配置快照分发」，2026-09-13 随端配置两表迁入 Auth Schema 本库直读而整体退役（消费者只有 Auth 自身，跨库分发无必要）。
- 版本化快照的发布一律使用 `setValueIfNewer`，禁止用 `increment` + `setValue` 手工拼装发布序列——两步之间无原子性，并发发布时旧信封可能在新信封之后落盘，污染后续冷启动加载。
- Key 不经过 `CacheKeyBuilder` 命名空间（跨服务共享键含 service 段无法对齐），由使用方契约常量统一定义并自行校验格式；缓存语义的数据仍必须走 `AspenCacheOperations`，禁止用本原语绕过 TTL 治理。
- `subscribe` 不提供可靠投递、重放或死信，断线期间的变更由使用方定义的自愈路径兜底（路由场景为下次变更或重启重发布）；可靠业务事件继续使用 RocketMQ。

common-cache 第一版不包含分布式锁、幂等、限流或任务锁。可靠业务事件使用 RocketMQ；锁和幂等在明确一致性、超时、失败与恢复语义后建立独立模块。

## 6. Gateway

路由分发的公共能力由两个模块承载，包根同为 `com.zax.aspen.common.gateway`。

`aspen-common-gateway-contract` 是纯契约模块，与 core 同级、零项目依赖、零基础设施依赖，可被 `api` 安全引用（契约消费方不会因此传递任何运行时）:

- `contract/GatewayRouteContract`：路由分发介质的 Redis Key、版本计数器 Key 与通知频道约定，附带 environment 格式校验；双方禁止私拼字符串。
- `contract/RouteCatalogSnapshot`、`RouteDefinitionSnapshot`、`RouteDefinitionPart`：信封与结构契约，构造期校验协议、语法与断言存在性，非法路由进介质前即失败。

`aspen-common-gateway` 承载发布/消费 SDK 与自动装配，只被实际运行的进程依赖:

- `GatewayRouteProperties`：`aspen.routes.environment` 配置绑定，发布侧与消费侧共用同一配置类，杜绝两侧取值漂移。
- `publish/RouteEnvelopePublisher`：发布原语，取号（INCR）→ 组装信封 → 版本守卫落盘（`setValueIfNewer`，仅当新于在途版本才 SET 并广播）→ Pub/Sub 携带版本号通知；不关心路由来源；发布时刻优先复用容器既有 Clock（如 common-database 的审计时钟），容器未声明时回退部署域默认时区，本模块不注册兜底 Clock Bean。
- `consume/RouteSnapshotStore`：消费 SDK，从 Redis 加载并持有内存快照，两段式解析（信封级损坏保留旧快照、单条损坏跳过），运行期零 Redis 访问。
- `AspenGatewayAutoConfiguration`：对齐 common-cache 的装配模式，`after` 固定在其后求值且仅当容器真实产出 `AspenRedisOperations` 时注册；无 Redis 的服务静默退避。

职责边界: `sys_route` 的领域读取、行到快照的转换、变更事件与启动编排留在 Admin；Spring Cloud Gateway 的路由映射与刷新集成留在网关进程；本模块不依赖任何业务模块与网关类库，Redis 访问经 common-cache 的 `AspenRedisOperations`。

## 7. Gen

包根为 `com.zax.aspen.common.gen`。公共生成工具模块, 第一版只提供枚举字典扫描:

- `GenDictScanner`: 按包范围扫描标注 `@GenDict` 的 `AspenEnum` 枚举, 产出 `List<GenDictDescriptor>`; 校验 dictCode 全局唯一, 未标注或非枚举类型忽略。
- `GenDictSink`: 目录投递 SPI; 使用方实现 `deliver(catalog)` 决定目录去向——Admin 的实现直接落库, 其他服务可实现远程上报到 Admin 的 internal 契约。
- `AspenGenAutoConfiguration` + `AspenGenProperties` + 通用启动 Runner: `aspen.gen.dict.enabled` 开启且容器存在 `GenDictSink` 时, 启动扫描一次并把目录投递给全部 Sink; `mode` 支持 `create-missing` 与 `resync`, `base-packages` 默认 `com.zax.aspen`。
- 落库不在本模块: Admin Biz 的 repository.sys/service.sys 消费目录写入 `sys_dict`/`sys_dict_item`, 其他服务经 admin-api 的 internal 契约上报; 模块因此不依赖 Jimmer、驱动或任何服务模块。

```yaml
aspen:
  gen:
    dict:
      enabled: false
      mode: create-missing
      base-packages:
        - com.zax.aspen
```

后续真正的代码生成能力 (如前端客户端生成) 落入本模块时, 必须同样遵守「只依赖 core、不触碰持久化」的边界。

## 8. Web

包根为 `com.zax.aspen.common.web`。公共 Web 运行约定模块，首版提供受众路径前缀、统一错误契约与 Trace ID，并承载路由套件（§8.3）的全部消费端：限流拦截器、操作日志拦截器与 springdoc 定制。

- `AspenWebProperties`：`aspen.web` 配置绑定，声明管理端、用户端与设备端三组「前缀 + Controller 包匹配规则」，默认值即平台约定，部署可整体改写前缀而不改代码；前缀格式错误或三组受众前缀/包规则重复时启动失败。
- `AspenWebAutoConfiguration`：实现 `WebMvcConfigurer`，在路径映射注册期按 Controller 类的包名给 `@RestController` 统一追加受众前缀；匹配对象是包名（Ant 规则、点号分隔），不是 URL。
- `trace/TraceId`：Trace ID 常量与生成器——`X-Trace-Id` 请求/响应头、MDC key `traceId`、8 字节 SecureRandom 的 16 位小写十六进制生成；外来值只接受 1-64 位 `[A-Za-z0-9_-]`，防止日志注入。
- `trace/AspenTraceIdFilter`：请求进入时读 `X-Trace-Id`（合法则复用、非法或缺失则生成），写入 MDC 并回写响应头，请求结束清理 MDC；注册序为最高优先级，覆盖完整请求周期。
- `error/AspenErrorCodeStatusMapper`：`ErrorCode` 到 HTTP 状态的映射，core 刻意不含 HTTP 语义，映射归 Web 层拥有；公共码精确映射，未登记的业务错误码默认 400。
- `error/AspenWebExceptionHandler`：`@RestControllerAdvice` 统一错误渲染（规则见下）。

```yaml
aspen:
  web:
    admin-api:
      prefix: /admin-api
      controller: "**.controller.admin.**"
    app-api:
      prefix: /app-api
      controller: "**.controller.app.**"
    device-api:
      prefix: /device-api
      controller: "**.controller.device.**"
```

配套约定：

- `biz` 的 Controller 按受众分目录：`controller/admin/{module}`、`controller/app/{module}`、`controller/device/{module}`、`controller/internal/{module}`（见《技术架构》7.5）；契约接口的 `PATH` 常量只写受众后的模块相对路径（如 `/sys/route`），前缀由 Controller 包位置决定，`api` 与 `biz` 都不得在路径常量里硬编码受众前缀。
- 命中受众规则的映射获得对应前缀，`internal` 及其余包不命中任何规则、不加前缀；internal 端点因此天然保持「不经网关暴露」的定位。
- 网关按 `/admin-api/**`、`/app-api/**`、`/device-api/**` 原样转发（不配 StripPrefix），服务本体与公开路径完全一致；RBAC 的 `upm_permission_api.path_pattern` 存含前缀的完整公开路径，`application` 列取值即受众标识（`admin-api`/`app-api`/`device-api`）。
- Admin 的种子路由 `/admin-api/**` 在首个 admin 受众 Controller 落地前是空壳锚点——网关可转发但服务侧无可命中映射（外部调用 404），属预期而非路由失效；原经 `/admin/sys/route` 暴露的路由管理端点已迁 `controller/internal/sys`，按内端定位不再经网关暴露。
- 模块只在 Spring MVC（servlet）类路径存在时装配；WebFlux 服务引入本模块会静默退避，不注册任何 Bean。spring-webmvc 是内部实现依赖，不经 `api` 传递，使用方自带 `spring-boot-starter-webmvc`。

### 8.1 统一错误契约

成功响应直接返回 DTO/VO，不套任何信封；失败响应统一为 RFC 9457 Problem Details（`application/problem+json`）：

```json
{
  "type": "about:blank",
  "title": "资源状态不允许执行当前操作",
  "status": 409,
  "detail": "路由已停用, 拒绝修改",
  "instance": "/admin-api/sys/route",
  "code": "COMMON.STATE_CONFLICT",
  "traceId": "3f9c2a7b81d04e55"
}
```

规则：

- `title` 取 `ErrorCode.defaultMessage`，`detail` 取 `BusinessException.detail`（必须可安全返回给调用方，敏感诊断只进日志或 `cause`）；`code` 与 `traceId` 是扩展字段，前者是稳定机器错误码，后者用于排查。
- `type` 在错误文档站真实存在前保持默认 `about:blank`，机器判别一律以 `code` 为准；不凭空发明会漂移的 URI。
- 状态映射：`INVALID_ARGUMENT`→400、`METHOD_NOT_ALLOWED`→405、`UNSUPPORTED_MEDIA_TYPE`→415、`RESOURCE_NOT_FOUND`→404、`UNAUTHORIZED`→401、`FORBIDDEN`→403、`STATE_CONFLICT`→409、`TOO_MANY_REQUESTS`→429（路由套件限流超限，§8.3）、`DEPENDENCY_UNAVAILABLE`→503、`INTERNAL_ERROR`→500；未登记的业务错误码默认 400，需要精确状态码时在映射表登记。
- 参数校验失败（`@Valid` 请求体、方法级校验、服务层 `@Validated` 的 `ConstraintViolationException`）、不可读请求体、缺少必填请求参数、参数类型不匹配统一映射为 400 `COMMON.INVALID_ARGUMENT`；服务层 `require` 校验惯用法抛出的 `IllegalArgumentException` 同样渲染 400（detail 外发其面向调用方书写的中文消息），避免客户端可修正的失败落入兜底 500；HTTP 方法不支持映射 405 `COMMON.METHOD_NOT_ALLOWED`（detail 指明被拒绝的方法）、请求媒体类型不支持映射 415 `COMMON.UNSUPPORTED_MEDIA_TYPE`（detail 回显客户端发送的 Content-Type）；未匹配路由统一 404 `COMMON.RESOURCE_NOT_FOUND`；兜底异常统一 500 `COMMON.INTERNAL_ERROR` 且 `detail` 只给安全消息，原始异常与堆栈只随 traceId 写日志。协议级客户端错误必须精确渲染为对应 4xx，不得落入兜底 500。

### 8.2 Trace ID

- 每个请求一个 traceId：请求头 `X-Trace-Id` 合法则复用（网关或调用方透传），否则服务本地生成；所有响应（成功与失败）都回写 `X-Trace-Id` 响应头，失败 body 的 `traceId` 字段与其一致。
- 日志关联：filter 把 traceId 写入 MDC，各服务以 `logging.pattern.level` 注入日志模式（Nacos `aspen-common` 统一下发），该请求期间每一行日志都携带同一 traceId；用户报 ID → 按日志 traceId 检索即可还原完整请求链路。
- 网关最终负责 Trace ID 的建立与跨服务透传（《技术架构》§9）；在其落地前，服务侧「有 header 用 header、无则自生成」的兜底保证单服务直连开发可用，网关能力落地时零改动。
- 过滤器按同步请求模型设计：`OncePerRequestFilter` 默认跳过异步再分发，引入 `Callable`/`DeferredResult` 等异步 Controller 前，完成线程与再分发阶段的 MDC 将无 traceId，届时须覆写 `shouldNotFilterAsyncDispatch` 或另行改造。

首版不提供 `R<T>` 包装、参数校验增强或 Trace 传播（W3C traceparent/Micrometer Tracing）；Problem Details 的 `type` 指向错误文档站、以及 Trace 上下文跨服务延续，在对应基础设施建立后再纳入本模块。

### 8.3 路由套件（aspen-common-route）

`aspen-common-route` 是薄注解契约模块：零项目依赖、编译类路径仅 Spring Web 注解 API、无任何 Bean 与自动装配。`api` 契约接口用它声明端点，替代裸 `@GetMapping`/`@PostMapping` 等 verb 注解；`path` 只写受众后的模块相对路径（与 §8 配套约定一致），受众前缀仍由 Controller 包位置决定。设计迁移自 pjcloud/NestJS 的 `@router.post` 装饰器，按网关分体架构做了三处裁剪：

- 不引入 `resType`/wrapper：返回值结构由 springdoc 从方法签名的强类型直接推断（如 `PageResult<TaskVO>`），且与「成功直返 DTO/VO、失败 Problem Details」的错误契约互斥方向不兼容。
- 不引入免鉴权 tag：鉴权权威在网关（`GatewaySecurityConfig` 公开路径），biz 侧注解无法也不得驱动网关放行。
- 每个注解属性必须有真实消费者，由 `RouteSuiteBoundaryTest` 架构测试强制；声明面不得先于消费面膨胀（NestJS 版 `rateLimit.key`/`limitType`、`RepeatSubmit` 均为无消费者的死配置，引以为鉴）。

五个 verb 注解（`GetRoute`/`PostRoute`/`PutRoute`/`DeleteRoute`/`PatchRoute`）元标注 `@RequestMapping(method=...)`，属性完全一致：

| 属性 | 类型 | 默认 | 消费者 |
| --- | --- | --- | --- |
| `path` | `vararg String` | 必填（经 `@AliasFor` 转发给 `RequestMapping.path`） | Spring MVC 映射注册 |
| `summary` | `String` | 必填 | springdoc 定制 + 操作日志 |
| `description` | `String` | 空串 | springdoc 定制 |
| `rateLimit` | `RateLimitSpec` | 60 秒 / 100 次 / `USER` | 限流拦截器 |
| `log` | `OperationTag` | `OTHER` | 操作日志拦截器 |

`RateLimitSpec` 携带 `windowSeconds`/`limit`/`scope`；`RateLimitScope.USER` 按认证主体限量，`GLOBAL` 全主体共享单桶。`OperationTag` 取值 `OTHER`/`INSERT`/`UPDATE`/`DELETE`/`GRANT`/`EXPORT`/`IMPORT`/`GENERATE`/`ADMIN`/`LOGIN`，是操作日志的业务分类标签。

消费端全部在 `aspen-common-web` 自动装配，业务服务零配置：

- `route/RouteSpecResolver`：`HandlerMethod` → 路由声明解析，兼容注解在 api 契约接口方法上（实现 Controller 零注解），解析结果按 `HandlerMethod` 缓存，请求期零反射合并开销。
- `route/RateLimitInterceptor` + `route/FixedWindowRateLimiter`：进程内固定窗口计数（纳秒级、无外部 IO），key 为「映射 pattern + HTTP 方法 + scope 主体」；超限抛 `BusinessException(CommonErrorCode.TOO_MANY_REQUESTS)`，经统一错误契约渲染 429 `application/problem+json`。`USER` 主体优先取 `RequestIdentityContext`（`security` 在类路径且装配时），无身份上下文时回退 `X-Forwarded-For` 首跳，再回退匿名共享桶；因此未接入 `common-security` 的服务（如 task-biz）自动退化为 IP 维度。`FixedWindowRateLimiter` 接受注入 `Clock`，过期窗口桶按容量阈值惰性清扫。
- `route/OperationLogInterceptor`：以独立 logger 名 `aspen.operation` 输出 INFO 单行结构化日志（tag、summary、method、pattern、status、costMs、subject、traceId、异常摘要），运维可按 logger 名独立路由到访问日志采集；请求/响应体捕获与落库属二期。
- `route/RouteOperationCustomizer`：springdoc `GlobalOperationCustomizer`，把 `summary`/`description` 写入 OpenAPI `Operation`。`common-web` 因此引入 `springdoc-openapi-starter-webmvc-ui` 3.x（Spring Boot 4 适配线），全部 biz 随依赖获得 `/v3/api-docs` 与 swagger-ui；网关不路由这些路径，仅内网可达，生产可经 `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled` 关闭。

语义与限制：

- 本地固定窗口是「每实例配额」：N 实例部署时限流总量约 N×limit，需要全局限量时二期换 Redis 实现（经 common-cache 受控通道），注解与业务代码不变。
- 固定窗口存在边界毛刺（窗口沿处两个窗口内可突发 2×limit），属该算法的已知语义，不做滑动窗口。
- `X-Forwarded-For` 仅网关链路可信，直连服务端口的调用方伪造 XFF 可稀释限流桶；绕过网关直连本属部署违规，由内网边界与信任链兜底。
- 限流与操作日志拦截器只对携带路由注解的端点生效；未注解端点走原生 MVC 路径，默认零额外开销。

二期再落地项：Redis 分布式限流实现替换、防重复提交（in-flight 锁 + 短窗去重，money 类端点 opt-in）、路由注解声明公开路径与网关白名单的一致性 fail-fast 校验、操作日志落库（sys_log）与请求体脱敏捕获。

## 9. Security

包根为 `com.zax.aspen.common.security`。本模块承载业务进程的最小信任链（防网关绕过与伪造身份头），只依赖 Servlet 能力与 common-database 的租户契约，无 Redis 依赖。曾承载的认证客户端配置快照分发 SDK 已随端配置两表迁入 Auth Schema（2026-09-13 归属修订）整体退役，权威说明见《认证数据模型》第 7 节。

- `trust/InternalTrustFilter`：校验 Gateway 注入的信任头（值来自环境变量 `ASPEN_GATEWAY_INTERNAL_SECRET`，两侧一致才放行）；缺失或不符时拒绝携带内部身份头的请求，未携带身份头的请求按匿名处理（internal 端点的保护由后续批次的服务身份收紧）。
- `trust/RequestIdentityContext`：请求级只读身份上下文（principalId、clientKind、tenantId），从网关注入的 `X-Aspen-*` 头解析。
- 装配 `TenantContextSupplier` 给 common-database 的 fail-closed 租户链，取代 Admin 临时的 `TenantHeaderFilter` 装配。
- 配置键：`aspen.security.enabled`（默认开启，本地无网关联调可关）、`aspen.security.trust-token`（期望的网关信任凭据，经环境变量注入）。

## 10. 服务接入与验收

`api` 最多依赖 core；`database` 和 `cache` 只允许由实际运行的 `biz` 按需依赖；`web` 由承载 MVC Controller 的 `biz` 引入，获得受众路径前缀（Admin Biz 已接入，Storage 在出现首个 Controller 时接入）。Admin Biz 已因 UPM 持久化模型引入 common-database、Jimmer KSP 和 MySQL 驱动，但仍未引入 common-cache。无外部数据库的上下文测试只在测试范围排除数据源与 Jimmer 自动装配，生产配置不允许借此绕过数据库依赖。

根 Gradle 配置在依赖声明阶段执行以下边界校验，即使模块暂无源码也不能绕过：

- core 禁止依赖其他项目模块、Spring、Jackson、Jimmer 或 Redis。
- database/cache/gen 只能依赖 core, 三者不互相依赖。
- gateway 只能依赖 cache 与 gateway-contract, 不依赖 database; gateway-contract 与 core 同级, 零项目依赖。
- route 与 gateway-contract 同级, 零项目依赖; web 只能依赖 core (错误契约)、route (路由注解) 与 security (编译期可选, 限流主体读取), 不依赖 database/cache/gateway。
- 所有 common 模块禁止依赖 `services` 目录下的模块。
- API 模块禁止依赖 database/cache、Jimmer、Spring Data、Redis 或 Spring Boot Starter; 路由契约例外只经纯契约模块 gateway-contract 进入。
- 所有模块禁止 JPA/Hibernate、MyBatis/MyBatis-Plus、Seata 和 Dubbo，并禁止动态或变化版本。

Testcontainers 的 JUnit Jupiter 和 MySQL 依赖别名已经登记在版本目录中，版本继续由 Spring Boot BOM 管理。Admin 的 @GenDict 播种已落地首个真实 Repository 集成测试, 使用 Testcontainers MySQL 执行迁移与写路径验证; 其余 common 测试仍不连接外部 MySQL 或 Redis。

验收必须满足：

1. core 编译类路径不包含 Spring、Jimmer 或 Redis。
2. common 不依赖任何 service，database/cache 不互相依赖，gateway 不依赖 database，gateway-contract 编译类路径不包含 Spring、Jackson 或 Redis。
3. 公共 Jimmer 映射接口能够被测试 Entity 组合继承并通过 KSP，Jimmer 元数据能识别乐观锁和逻辑删除。
4. 非法分页、批次、Cache、TTL、Key、业务组白名单、大小和 Web 受众前缀配置启动失败。
5. 用户提供的 Clock、数据库限制、审计拦截器、CacheManager、RedisTemplate、CacheOperations、Trace ID 过滤器、错误码状态映射器、统一异常渲染器或路由套件 Bean（解析器、限流器、拦截器、springdoc 定制）可以覆盖默认 Bean。
6. 未声明 Cache 默认不可创建，Redis 故障不会被公共层吞掉。
7. 完整 Gradle `check` 与 Admin 启动测试通过。
8. `controller/admin`、`controller/app` 与 `controller/device` 包下的 `@RestController` 映射分别携带 `/admin-api`、`/app-api`、`/device-api` 前缀，`internal` 及其余包不加前缀；前缀可经 `aspen.web.*` 配置整体覆盖。
9. 失败响应统一 `application/problem+json`，携带 `code`（机器错误码）与 `traceId`，HTTP 状态与错误码映射一致，兜底异常不泄露内部细节；成功响应直接返回 DTO/VO 且带 `X-Trace-Id` 响应头，body 内 `traceId` 与响应头一致。
10. 路由套件：verb 组合注解在 api 契约接口方法上可被 MVC 解析为映射（实现 Controller 零注解）；限流超限第 `limit+1` 次请求返回 429 `application/problem+json`（`COMMON.TOO_MANY_REQUESTS`）；`aspen.operation` logger 输出操作日志行；springdoc 文档的 summary 与注解一致；`aspen-common-route` 源码 import 只允许 `org.springframework.web.bind.annotation`，且注解每个属性在 common-web 存在消费者（架构测试强制）。

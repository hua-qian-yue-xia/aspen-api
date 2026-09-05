# Aspen Common 基础模块设计

> 文档状态：core/database/cache 首版已实现，`aspen-common-web` 待建  
> 文档基线：2026-09-06  
> 关联文档：[技术架构](./technical-architecture.md)

## 1. 目的与边界

`aspen-common` 为 `services` 下的微服务提供稳定、可选择的基础能力，不是一个包含所有依赖的公共工具包。第一版包含：

```text
aspen-common/
├── aspen-common-core/
├── aspen-common-database/
└── aspen-common-cache/
```

依赖方向只有：

```text
aspen-common-database -> aspen-common-core
aspen-common-cache    -> aspen-common-core
```

`core` 不依赖 Spring、Web、Jackson、Jimmer 或 Redis；`database` 与 `cache` 不互相依赖；所有 common 模块禁止依赖任何服务的 `api` 或 `biz`。不存在 `common-all`，没有数据库或缓存需求的服务不引入对应模块。

源码注释遵守项目统一规范：注释正文使用中文，专有名称保留原文，标点使用英文字符，句尾不加句号。类、接口、枚举、对象、字段和方法必须有说明职责或约束的有效 KDoc，重要实现边界补充行注释。数据库实体和字段的 KDoc 必须详尽：类级注释说明职责与典型使用场景，字段有具体使用场景、取值约定、生命周期或对其他流程的影响时必须逐一写清楚，仅列名自解释且无附加语义的简单字段可不写字段注释。Jimmer 实体列名与属性名蛇形一致时不声明 `@Column`，由 Jimmer 自动解析，该规则由架构测试强制检查。完整规则和示例见《技术架构》7.8 节。

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
| `Gender` / `EnabledStatus` | 性别（语义对齐 ISO/IEC 5218）与通用启停状态枚举 |

业务错误码仍归拥有者 `*-api/error/{group}`。成功 HTTP 响应直接返回 DTO/VO；后续 `aspen-common-web` 将 `BusinessException` 转换为 RFC 9457 Problem Details，并保留正确 HTTP 状态码。core 不提供 `R<T>` 或 `ApiResponse<T>`。

错误契约中的机器错误码保持稳定英文，例如 `COMMON.INVALID_ARGUMENT`；默认错误消息和允许返回给调用方的 `BusinessException.detail` 统一使用中文。Java 类型、Cache 名称、数据库结构、下游地址和原始异常消息不得进入对外 `detail`，只允许写入受控日志或保存在异常 `cause` 中。

API 只有实际使用上述公共类型时才声明：

```kotlin
dependencies {
    api(project(":aspen-common-core"))
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
    implementation(project(":aspen-common-database"))
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)
}
```

除 Spring Boot BOM 版本约束外，common-database 只通过 Gradle `api` 暴露 `jimmer-sql-kotlin`、core 和编译所需注解。Jimmer Spring Boot Starter 保持为 `implementation` 运行实现，避免业务模块的编译 API 被整个 Starter 扩大。Jimmer `0.11.7` 的 KSP 代码引用 `com.fasterxml.jackson.annotation`，Jackson 3 databind 也以该注解包作为兼容 API；common-database 因此传递 `jackson-annotations`，但不引入 Jackson 2 databind。

SQL 日志、Dialect、Schema 验证和 JDBC 超时继续使用 `jimmer.*` 原生属性。生产环境保持 `jimmer.inline-sql-variables=false`，禁止在日志中展开敏感 SQL 参数。

## 5. Cache

包根为 `com.zax.aspen.common.cache`。公共缓存模块提供严格 Key、显式 TTL、受控 JSON 序列化、`RedisCacheManager` 和 `AspenCacheOperations`。业务代码不直接操作 `RedisTemplate`，服务内的 `cache/redis` 封装组合这些基础操作并决定回源和失败策略。

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
val key = CacheKey("upm", "user", "42")
cacheOperations.put("upm-user", key, userView)
val cached = cacheOperations.get<UserView>("upm-user", key)
```

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

common-cache 第一版不包含分布式锁、幂等、限流、Redis Pub/Sub 或任务锁。可靠业务事件使用 RocketMQ；锁和幂等在明确一致性、超时、失败与恢复语义后建立独立模块。

## 6. 服务接入与验收

`api` 最多依赖 core；`database` 和 `cache` 只允许由实际运行的 `biz` 按需依赖。Admin Biz 已因 UPM 持久化模型引入 common-database、Jimmer KSP 和 MySQL 驱动，但仍未引入 common-cache。无外部数据库的上下文测试只在测试范围排除数据源与 Jimmer 自动装配，生产配置不允许借此绕过数据库依赖。

根 Gradle 配置在依赖声明阶段执行以下边界校验，即使模块暂无源码也不能绕过：

- core 禁止依赖其他项目模块、Spring、Jackson、Jimmer 或 Redis。
- database/cache 只能依赖 core，二者不能互相依赖。
- 所有 common 模块禁止依赖 `services` 目录下的模块。
- API 模块禁止依赖 database/cache、Jimmer、Spring Data、Redis 或 Spring Boot Starter。
- 所有模块禁止 JPA/Hibernate、MyBatis/MyBatis-Plus、Seata 和 Dubbo，并禁止动态或变化版本。

Testcontainers 的 JUnit Jupiter 和 MySQL 依赖别名已经登记在版本目录中，版本继续由 Spring Boot BOM 管理。首版 common 测试不连接外部 MySQL 或 Redis，因此不把未使用的 Testcontainers 依赖加入模块运行类路径；首个真实 Repository 集成测试落地时再按需使用。

验收必须满足：

1. core 编译类路径不包含 Spring、Jimmer 或 Redis。
2. common 不依赖任何 service，database/cache 不互相依赖。
3. 公共 Jimmer 映射接口能够被测试 Entity 组合继承并通过 KSP，Jimmer 元数据能识别乐观锁和逻辑删除。
4. 非法分页、批次、Cache、TTL、Key、业务组白名单和大小配置启动失败。
5. 用户提供的 Clock、数据库限制、审计拦截器、CacheManager、RedisTemplate 或 CacheOperations 可以覆盖默认 Bean。
6. 未声明 Cache 默认不可创建，Redis 故障不会被公共层吞掉。
7. 完整 Gradle `check` 与 Admin 启动测试通过。

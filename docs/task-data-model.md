# Aspen Task 数据模型

> 文档状态：首版 4 表 Entity、域枚举、初始迁移（含 Quartz 集群表）与管理/调度/HTTP 投递链路已建立；执行日志回传通道（`task_execution_log` + 内部回传契约）已落地；管理端 RBAC、消息通道投递、心跳与协作式取消待建  
> 文档基线：2026-09-12  
> 关联文档：[技术架构](./technical-architecture.md)（14.1 节为调度语义权威）｜[Common 模块设计](./common-module-design.md)｜[Admin UPM 数据模型](./admin-upm-data-model.md)

## 1. 目标与来源

Aspen Task 是全体系统一任务微服务，承载所有周期任务、固定时点任务与补偿扫描任务的调度与投递，业务 `biz` 一律不自建定时循环。首版 3 张业务表 + Quartz 集群运行时表，代码位于：

```text
aspen-task/aspen-task-api/src/main/kotlin/com/zax/aspen/task/api/   # 契约、入参出参与域枚举
aspen-task/aspen-task-biz/src/main/kotlin/com/zax/aspen/task/biz/   # 管理、调度与 HTTP 投递
aspen-task/aspen-task-biz/src/main/resources/db/migration/          # V001 业务表 + V002 Quartz 表
```

设计参考了 Friendship-and-chat-api（芋道 infra 定时任务）的 Quartz JDBC Cluster + 执行日志 + 手动触发形态；其 11 项缺口在本模型中修正（见第 4、5 节）：handlerName 全局唯一且只支持本地 Bean 调用、仅 cron 触发、`Thread.sleep` 阻塞式重试、无超时控制、多租户共用一条执行日志、DB 与 Quartz 双写靠手动 sync 兜底、misfire 走默认值等。

投递通道为 v1 权威决策：**仅同步 HTTP**——内部微服务与外部项目共用同一目标模型（方法、URL、请求头、请求体、超时、重试），执行结果以 HTTP 响应判定；《技术架构》14.1.4 规划的 Outbox + RocketMQ 通道是未来扩展位，不与本通道并存。

任务定义是平台级配置（全体租户共享的调度资产），三张表均不做租户隔离；租户是**执行维度**——每次触发达成「每租户一条执行记录」，`tenant_id` 只作普通列与索引（跨库不可外键 `upm_tenant`），租户合法性由投递前的租户解析与目标服务的 fail-closed 过滤器共同保证。Task 使用独立 Schema `aspen_task`。

## 2. 表清单

| 领域 | 表 | 职责 |
| --- | --- | --- |
| 定义 | `task_definition` | 任务定义：触发规则（cron/固定间隔/一次性）、HTTP 目标、超时重试、租户圈定、misfire/并发策略与启停状态 |
| 定义 | `task_tenant` | `SELECTED_TENANTS` 圈定的租户清单，(definition_id, tenant_id) 唯一 |
| 执行 | `task_execution` | 逐租户逻辑执行记录：executionId 主键、attempt、状态机、失败类别、HTTP 状态与响应片段 |
| 执行 | `task_execution_log` | 执行过程日志：目标服务按 executionId 顺序上报的步骤/进度/告警日志，回传幂等 |
| 运行时 | `QRTZ_*`（11 张） | Quartz JDBC Cluster 调度运行时状态，上游官方 DDL 原样引入，不对管理端暴露 |

## 3. Jimmer 映射约定

- 与 UPM/SYS/Storage 一致：语义化自增主键（`task_definition.definition_id`）、`DATETIME(3)` 映射 `LocalDateTime`、业务默认值经 Jimmer `@Default` 与 SQL 默认值同步（`@Default` 写枚举 NAME，列默认值写枚举 code）、列名与属性蛇形一致零 `@Column`、`headers` 经 `@Serialized` 与 JSON 列互转。
- 原子组合：`task_definition` 用 `MutableAuditEntity`（乐观锁 + 逻辑删除，误删可恢复）；`task_tenant` 用 `CreateAuditEntity`（不可变关系行，物理删除）；`task_execution` 用 `AuditableEntity + VersionedEntity`（过程数据，乐观锁保护状态迁移，保留期物理删除，不声明删除审计防行膨胀）。
- 无对象关联：三张表互不声明 `@ManyToOne`——执行记录靠 `task_code` 快照在任务删除后仍可读，租户清单由 Service 整体替换（先删后插），跨库 `tenant_id` 无法建关联。
- 枚举全部实现 `AspenEnum` 并带 `@GenDict`（group=`task`）：触发类型、租户圈定、misfire 策略、并发策略、HTTP 方法、触发来源、执行状态、失败类别；启停复用 common-core `EnabledStatus`。持久化经 `AspenEnumProviders` 按 code 与数据库小写值互转。

## 4. 任务定义与触发规则

- `task_code` 全局唯一且**永久占用**（对标 `sys_route`/`storage_config`，唯一键不做 IFNULL 折算）：调用方以编码为稳定契约，执行记录以编码快照回溯历史；确需同码须恢复逻辑删除行而非新建。`task_name` 随时可改。
- 触发类型三选一，各自字段约束由 Service 校验（DTO Bean Validation 只兜形状）：
  - `CRON`：`cron_expression` 必填（Quartz 6/7 位制，保存前 `CronExpression` 校验 + 下次触发预览），`timezone_id` 必填 IANA 时区（Quartz Trigger 显式 `inTimeZone`，禁止容器默认时区）；
  - `FIXED_INTERVAL`：`interval_seconds` 必填（≥1s，建议 ≥60s，避免无界高频触发）；
  - `ONE_TIME`：`fire_at` 必填带时区绝对时间且须在未来；触发完成后 Trigger 自然结束，任务不自动停用（重设 `fire_at` 并更新可再次执行）。
- 每任务显式配置（14.1.4 基线）：`timeout_seconds`（HTTP 请求级超时，默认 30）、`max_attempts`（含首次的总尝试次数，默认 1）、`backoff_seconds`（重试退避间隔，默认 60）、`misfire_policy`（`fire_once` 错过补执行一次 / `skip` 跳到下个时点，映射 Quartz Misfire 指令，禁止默认行为）、`concurrent_policy`（`allow` 允许并行触发 / `skip` 存在 RUNNING 执行时本轮跳过并记 SKIPPED）、`owner_account`（负责人，告警与审计归因）。
- HTTP 目标：`http_method`（GET/POST/PUT/DELETE/PATCH）、`target_url`（仅 http/https）、`headers`（JSON 键值，外部目标的鉴权头在这里配）、`body`（原样透传的请求体文本）。URL、headers 值与 body 支持租户占位符 `{tenantId}`、`{tenantCode}`、`{tenantName}`，投递前按当次执行的租户渲染。
- 定义表是唯一权威，`QRTZ_*` 是调度运行时状态：创建/更新/启停/删除一律「先同步 Quartz、后落定义表」，Quartz 同步失败即整体失败，不向调用方报告启用成功；两套状态出现漂移时由启动对账器（`TaskQuartzReconciler`）告警并按定义表受控修复（缺失补建、多余清理），不静默覆盖。

## 5. 租户圈定与执行语义

- `tenant_scope` 二选一：`ALL_TENANTS` 每次触发**实时**拉取 Admin 启用租户（状态启用且在 `valid_from/valid_to` 窗口内，经 Admin internal 契约 `GET /internal/upm/tenant/enabled`，短 TTL 缓存兜底防抖）——新开租户自动纳入下一次触发，无需改任务；`SELECTED_TENANTS` 按管理端多选清单（`task_tenant` 行集）执行，清单变更即时生效。
- 每次触发的执行面是「任务 × 租户」的笛卡尔展开：每个租户一条独立的逻辑执行（executionId），单租户失败不影响其他租户，修正参考项目「多租户共用一条日志、单租户失败语义含糊」的缺口。
- `execution_id` 是 `task_execution` 主键（VARCHAR，逻辑执行唯一约束落库）：计划触发 `task-{definitionId}-f{scheduledFireTimeEpochMillis}-{tenantId}`；人工触发 `task-{definitionId}-m-{requestId}-{tenantId}`，同一 `requestId` 重放不会创建第二次逻辑执行（`request_id` 列冗余存储并建索引，管理端预查重）。重试沿用同一 `executionId` 递增 `attempt` 列。
- 触发流程：Quartz 集群抢占 Trigger（实例获得权由 Quartz 数据库锁保证）→ 按定义读取当前配置（JobDataMap 只带 `definitionId`，修正参考项目参数快照陈旧缺口）→ 并发策略检查（`skip` 且存在 RUNNING 执行 → 逐租户记 SKIPPED 后返回）→ 解析租户清单 → 逐租户插入 RUNNING 执行行（主键冲突即同逻辑执行已存在，幂等跳过）→ 提交有界线程池并发投递 → 回写结果。
- 失败重试由状态机编排：失败且 `attempt < max_attempts` 时创建一次性 Quartz Trigger（延迟 `backoff_seconds`）复用 executionId 再次投递，不用阻塞 sleep 占用调度线程；重试耗尽保持 FAILED。管理端可对失败执行手动重派（attempt+1 立即投递）。
- 执行状态机：`RUNNING → SUCCESS / FAILED`，另有 `SKIPPED`（并发策略跳过）；失败类别 `failure_kind` 细分 `timeout / http_error / connection_error / target_rejected / interrupted`，修正参考项目只有异常 message 的可观测缺口。`response_snippet` 截断存储（默认 2000 字符）。
- 投递请求头：租户头 `X-Aspen-Tenant-Id`（常量收敛于 common-core）+ 溯源头 `X-Aspen-Task-Id / X-Aspen-Execution-Id / X-Aspen-Attempt / X-Aspen-Fire-Time`（常量收敛于 task-api `constant`）；目标服务以 executionId 幂等、按 attempt 识别合法重试，租户上下文缺失时 fail-closed 拒绝（common-database 既有语义）。
- **执行过程日志回传**：HTTP 投递只回写一次终态，执行中的走向经回传通道补齐——目标服务从溯源请求头取得 `executionId`，按步骤调 `POST /internal/task/execution-log`（task-api `TaskLogReportApi` 契约，批量携带自增 `seq`、级别与消息）；`(execution_id, seq)` 唯一约束使重复上报幂等跳过、乱序到达不破坏排序，`logged_at` 记录目标侧时间、`created_at` 记录平台接收时间。管理端经 `GET /admin-api/task/execution/{executionId}/log` 按 seq 升序查看完整走向；外部项目可选遵循（不回传只影响可观测性，不影响执行语义）。该通道是依赖方向表「business-biz -> aspen-task-api 回传任务执行结果」的首个落地形态，未来心跳与协作式取消沿同一通道扩展。

## 6. HTTP 目标校验（SSRF 防护）

- 每次投递前经 `TaskHttpGuard` 校验：仅允许 `http`/`https` 协议；解析目标主机全部 `InetAddress`，拒绝环回（127.0.0.0/8、::1）、私有（10/8、172.16/12、192.168/16、fc00::/7）、链路本地（169.254/16、fe80::/10）、任意本地（0.0.0.0）、组播与 CGNAT（100.64/10）地址；DNS 解析失败拒绝。
- 内部微服务目标（如 admin-biz）经配置白名单 `aspen.task.http.allowed-internal-hosts`（`host` 或 `host:port` 条目）显式放行；白名单外的内网目标一律拒绝，防投递配置被滥用为内网探测。
- HTTP 客户端（JDK HttpClient）禁跟随重定向（防重定向绕过地址校验）、请求级超时（`timeout_seconds`）、响应体读取上限（默认 64KB）；连接/读取异常按失败类别归类记录。

## 7. 可用性与恢复语义

- `task_definition` 乐观锁 + 逻辑删除：并发修改被拒（刷新重试），误删可恢复；恢复或重建后由启动对账器补齐 Quartz 运行时。删除任务 = 定义行逻辑删除 + Quartz Job/Trigger 清理 + `task_tenant` 物理清理；执行记录保留供审计。
- `task_execution` 与 `task_execution_log` 物理删除走保留期治理：`housekeeping` 内置系统任务（每日）清理超过保留天数（默认 30，可配）的执行记录与日志（日志不与执行记录建外键，两条保留期扫描独立推进，避免删除顺序耦合），并回收僵尸 RUNNING（启动时与每日各扫一次：RUNNING 且超过回收窗口的行置 FAILED/interrupted——实例崩溃遗留）。
- 集群语义：全部实例共享 Task Schema 与 Quartz JobStore（`isClustered`、唯一实例 ID、数据库锁抢占与故障接管）；「哪个实例获得 Trigger」由 Quartz 保证，「投递不重复」不保证——语义为 At Least Once + 目标幂等。
- 事务边界：Quartz 调度操作使用自身连接独立提交，不参与定义表事务；「先 Quartz 后 DB」的窗口期漂移（DB 回滚遗留孤儿 Trigger、DB 提交失败）由对账器闭环，这是受控选择而非缺陷。
- 全租户解析失败（Admin 不可达且缓存未命中）发生在执行展开之前：整轮跳过并记录 ERROR 日志（任务编码 + 轮次），下一轮自然重试，不静默吞掉；指定租户任务读取本地 `task_tenant` 清单，不受 Admin 可用性影响。

## 8. 管理契约与路由

- 管理面契约 `TaskApi`（task-api `contract/`，Controller 位于 `controller/admin/task` 经 common-web 自动挂 `/admin-api/task/**`）：创建/更新/启停/删除/手动触发/详情/分页/下次触发预览/执行记录分页/失败重派。入参 Bean Validation（jakarta）+ Service 层语义校验（require），出参为不可变 VO；分页复用 common-core `PageQuery/PageResult`，受 `DatabaseLimits` 约束。
- 手动触发 `POST /admin-api/task/{definitionId}/trigger`：可选 `requestId` 幂等键（缺省服务端生成）；走与计划触发完全相同的投递链路（Quartz 立即触发 + MANUAL 来源标记），便于在准生产环境演练任务。
- 网关路由种子（Admin sys V005）：`/admin-api/task/**` → `lb://aspen-task-biz`（sort_order=10），Admin 兜底路由 `/admin-api/**` 抬升至 sort_order=100——sys_route.sort_order 是 INT UNSIGNED 无法取负，用「具体前缀靠前、兜底靠后」实现优先匹配。
- 租户头接收侧（Admin）以 opt-in 装配（`aspen.admin.tenant-header.enabled`，默认关）：解析 `X-Aspen-Tenant-Id` 填充 `TenantContextSupplier`，仅限网关/任务服务内网调用链启用；RBAC 就绪后由统一认证接管。

## 9. Schema 管理

初始迁移位于 `db/migration/` 根（单业务域不分组）：`V001__create_task_schema.sql`（task_definition → task_tenant → task_execution，满足唯一键与引用顺序）、`V002__create_quartz_schema.sql`（Quartz 上游 MySQL InnoDB 官方 DDL 原样引入，文件头标注来源与勿改；`spring.quartz.jdbc.initialize-schema=never`，表结构升级随 Quartz 版本走官方脚本）、`V003__create_task_execution_log.sql`（执行过程日志表）。Task 的版本序列独立于 Admin（Admin sys 已用 V002–V005、upm 用 V001），服务内全局唯一、只增不改。所有表 InnoDB、`utf8mb4`、`utf8mb4_0900_ai_ci`；业务表不与 `QRTZ_*` 建外键，运行时表的生命周期归 Quartz 管理。

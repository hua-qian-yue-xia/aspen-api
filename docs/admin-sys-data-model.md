# Admin SYS 数据模型

> 文档状态：字典表已改为全局引用数据并支持 @GenDict 枚举播种, 参数表保持租户级, 路由表与 Redis 发布链路已建立, 分发契约已拆入 gateway-contract 纯契约模块, 字典对象关联已声明 (见 3 节), 路由种子已对齐受众前缀 (V004, 见 7 节); 管理界面与对外查询 API 待建  
> 文档基线：2026-09-12  
> 关联文档：[Admin UPM 数据模型](./admin-upm-data-model.md)｜[Common 模块设计](./common-module-design.md)

## 1. 目标与来源

Admin SYS 使用 Jimmer 建模字典、运行参数与网关路由, 首版覆盖 4 张表; 统一认证批次追加 `sys_auth_client` 与 `sys_auth_login_method` 两张认证配置表 (V006, 结构与语义权威在《认证数据模型》, 本文不重复展开), 代码位于:

```text
services/aspen-admin/aspen-admin-biz/src/main/kotlin/com/zax/aspen/admin/biz/entity/sys/
services/aspen-admin/aspen-admin-biz/src/main/resources/db/migration/sys/
```

数据按归属分为两类: 字典与路由是平台引用数据, 全体租户共用, `sys_dict`、`sys_dict_item` 与 `sys_route` 不做租户隔离; 参数是租户业务数据, `sys_config` 保持租户隔离。判据是「租户之间是否会各持一份不同数据」, 而不是表名前缀。将来出现按租户定制字典文案的真实需求时, 以独立覆盖表 (`tenant_id + dict_item_id + 覆盖字段`) 扩展, 不回填租户列。

本轮已建立 Entity、版本化 Schema、@GenDict 播种链路 (repository.sys、service.sys 与 internal 上报契约) 与路由发布链路 (Redis 版本信封 + Pub/Sub 通知), 字典与参数的管理界面、对外查询 API 仍待建。实体属于 Admin Biz 内部持久化模型, 管理与查询走 Service; 唯一例外是 internal 上报契约, 只接收受控的字典目录结构, 不暴露实体。

## 2. 表清单

| 领域 | 表 | 职责 |
| --- | --- | --- |
| 字典 | `sys_dict` | 全局字典定义、编码、分组、内置标记和启停状态 |
| 字典 | `sys_dict_item` | 字典项值、显示文本、层级关系和前端展示属性 |
| 参数 | `sys_config` | 租户级参数键值、类型声明、内置与敏感标记 |
| 路由 | `sys_route` | 网关动态路由定义、断言与过滤器、匹配顺序和启停状态 |
| 认证 | `sys_auth_client` | 端注册与机器密钥摘要（权威见 [认证数据模型](./auth-data-model.md)） |
| 认证 | `sys_auth_login_method` | 端×登录方式策略：认证方式、验证码闸门、密码策略（权威见 [认证数据模型](./auth-data-model.md)） |

## 3. Jimmer 映射约定

- 映射风格与 UPM 保持一致: 数据库自增 `BIGINT UNSIGNED` 语义化主键（表名去 `sys_` 前缀加 `_id`，如 `sys_dict.dict_id`）、`DATETIME(3)` 映射 `LocalDateTime` 并按部署域统一时区存取、业务默认值通过 Jimmer `@Default` 同步 SQL 默认值、可更新实体组合乐观锁与 `deleted_at` 时间戳逻辑删除。
- SYS 实体直接组合 `aspen-common-database` 的 `MutableAuditEntity` 与 `TenantScopedEntity`, 主键由各实体自行声明, 不创建业务包级纯组合接口, 也不依赖 `entity.upm` 的任何类型。
- 各表启停状态字段已切换为 common-core 的 `EnabledStatus` 枚举, 持久化经 `AspenEnumProviders` 按 code 与数据库小写值互转, 数据零迁移; 标签色型、值类型等展示与解析约定字段仍使用 `String`。
- `sys_route` 的 `predicates`、`filters` 与 `metadata` 是按语义命名的专用 JSON 列, 经 Jimmer `@Serialized` 映射为类型化集合与映射, 服务层不做手工 JSON 解析, 通用约定见《技术架构》12.5 节。
- 对象关联与 UPM 同规则: `sys_dict_item` 声明 `dict` 的 `@ManyToOne` 与 `parent` 自引用 (标量保留为同名 `@IdView`), `sys_dict` 声明 `dictItems` 反向集合; `parent_id` 对象级可关联但数据库仍不设外键, 树一致性由 Service 维护; `sys_config.tenant_id` 受 common 租户原子约束不建对象关联; `sys_route` 无引用列, 不涉及关联。

## 4. 字典设计

- `sys_dict` 以 `dict_code` 全局唯一定位一个字典; `is_built_in` 标记系统内置字典, Service 必须拒绝删除内置字典, 只允许调整显示属性。
- `sys_dict_item` 以 `dict_id + item_value` 唯一, 保证同字典内值不重复, 支持按值幂等 upsert。
- `dict_group` 为字典分组, 供管理界面按域筛选, 约定小写下划线格式 (如 common/upm); 播种与手工创建均必填, 默认 common。
- `parent_id` 可空且不设数据库外键, 支持省市区等树形级联场景; 移动子树时由 Service 在一个本地事务内维护层级一致性, 树形字典查询按 `idx_sys_dict_item_parent` 或 `(tenant_id, dict_id, sort_order)` 索引执行。
- `is_default` 表示表单默认选中项, 同字典下只应有一个默认项, 由 Service 写入时校验。
- `tag_type` 已统一为 `color`, 取值为 common-core `EnumColor` 的调色板令牌（5 个语义色与 11 个调色板色）或 `#RRGGBB` 色值, 与业务枚举的 `color` 共用同一套约定, 支撑取值较多的字典项区分展示; `css_class` 保存自定义样式类; 两者只影响展示, 不参与业务判断。
- 字典翻译缓存以 `dict_code` 为缓存键并定义失效策略, 缓存不是权威数据。

## 5. 枚举字典播种

- `@GenDict(code, name, group)` 标注在 `AspenEnum` 枚举上声明字典镜像: code 对应 `dict_code`, name 对应 `dict_name`, group 对应 `dict_group`; 字典项由枚举常量生成, `item_value=code`、`item_label=description`、`color=color`、`sort_order` 取声明顺序。
- 链路: common-gen 在启动时扫描并把目录投递给容器内的 `GenDictSink` (通用 Runner 属于 common-gen, 使用方只提供 Sink 实现), Admin 的 Sink 实现经 service.sys 直接落库; 其他服务经 admin-api 的 `POST /internal/gen/dict-report` 上报, 由 `aspen.gen.dict.enabled` 控制, 默认关闭, 生产环境禁止开启。admin-api 只发布 MVC 契约, Feign 客户端随首个消费方服务及其 Cloud 设施一起落地——把 openfeign 注解放进 api 模块会经 implementation 传染到全部消费方运行时, 在没有完整 Spring Cloud Starter 时启动即失败。
- 播种幂等: 默认 `create-missing` 只新增缺失的字典与字典项, 绝不修改已有行, 保护运营对展示的定制; `resync` 强制回写 dict_name、dict_group、item_label、color、sort_order, 不触碰 status、is_default、css_class 与 parent_id; 枚举只增不减, 不清理孤儿字典项。
- 生成的字典一律 `is_built_in=true`, 其字典项禁止运营增删值, 只允许调整展示属性, 由未来的字典管理 Service 强制校验——枚举仍是唯一权威取值来源, 字典只是展示镜像。
- 上报端点属于 internal 契约, 不经网关暴露; v1 无鉴权, 依赖「默认关闭 + 网络不暴露」兜底, auth 服务就绪后补齐。

## 6. 参数设计

- `sys_config` 以 `tenant_id + config_key` 唯一, 值统一保存为字符串。
- `value_type` 约定取值为 `string/number/boolean/json`; Service 读取时按类型解析并校验, 写入时拒绝与声明类型不匹配的值。
- `is_built_in` 标记系统运行依赖的内置参数, 禁止业务删除, 只允许调整值。
- `is_sensitive` 标记敏感参数, 其值不得进入日志、导出文件和未脱敏的接口响应; 与 UPM 安全字段同等对待。
- 参数是运行配置的业务侧补充, 不能替代 Nacos 管理的中间件与运行时配置; 高频读取的参数必须走缓存并定义回源行为。

## 7. 路由设计

- `sys_route` 存放 Spring Cloud Gateway 的动态路由定义, `route_code` 全局唯一并直接作为 Gateway 的 route id, 创建后不可修改; 唯一键与逻辑删除并存意味着编码一经使用即永久占用, 删除后不可重建同码, 误删恢复走逻辑删除行恢复机制。
- 路由是平台基础设施配置, 全体租户共用同一套规则, 与字典同为全局引用数据, 不做租户隔离。
- 路由断言按受众前缀组织: 面向 Admin 的路由匹配 `/admin-api/**` 并原样转发、不配 StripPrefix——服务本体经 `aspen-common-web` 的包前缀机制 (Controller 按 `controller/admin|app` 分目录) 原生携带同一前缀, 网关与服务路径完全一致; `internal/**` 端点永不进入网关路由。`upm_permission_api.path_pattern` 同样存含前缀的完整公开路径, `application` 列取值即受众标识 (`admin-api`/`app-api`)。
- `uri` 只允许 `lb://` (经 Nacos 服务发现负载均衡) 与 `http://`、`https://` 直连地址, 由 Service 写入时校验; `predicates` 与 `filters` 以 `[{name, args}]` 结构类型化存储, Gateway 侧解析为 Spring Cloud Gateway 定义; `metadata` 保存路由级参数 (如 response-timeout)。
- `sort_order` 映射路由匹配顺序, 数值小者先匹配; `status=disabled` 的路由不进入发布快照, 停用即从 Gateway 生效面移除。
- 发布链路: 路由增删改事务提交后与 Admin 启动时, Service 全量构建带单调递增版本号的 JSON 信封, 一条 `SET` 原子替换 Redis 单 Key, 并经 Pub/Sub 频道携带版本号通知 Gateway 刷新。Redis 只是分发介质, `sys_route` 是唯一权威源, 可随时全量重建; 版本号比对防止乱序。列内 JSON 结构损坏在行映射阶段整体失败并告警, 结构合法但语义非法的单行 (如缺断言) 跳过并告警。
- Gateway 通过只读 `RouteDefinitionRepository` 消费快照, 运行期不访问 Redis, 已加载路由不受 Redis 故障影响; actuator 直写路由被禁止, 数据库是唯一写入通道。路由管理 HTTP 契约按 §5 同款 internal 模式默认关闭, RBAC 就绪后转正式管理 API。
- 变更通知不持久, Gateway 断线期间的发布靠下次变更或重启自愈; 周期对账在统一任务服务建立后接入, 此前不引入临时 @Scheduled。
- 分发协议的全部锚点收敛在 common 侧两个模块: `aspen-common-gateway-contract` (纯契约, 零基础设施依赖, 信封结构类型与 `GatewayRouteContract` 的 Redis Key、通知频道、environment 规则) 与 `aspen-common-gateway` (`publish`/`consume` 发布原语与消费 SDK), Admin 与 Gateway 各自引入对接。修改分发协议时只改这两处, 禁止两侧私拼 Key/频道字符串或自定信封字段。
- 职责边界: common-gateway 只承载协议与介质操作 (取号、原子替换、通知、加载持有), `sys_route` 的领域读取、行到快照的转换、变更事件与启动编排永远留在 Admin (sys 的领域职责); Spring Cloud Gateway 的路由映射与刷新集成留在网关进程。
- 演进边界与抽取决策 (2026-09-07 留档): 原条款要求「第二个消费者出现之前禁止预建 common-gateway 类公共包」, 本次抽取依据与之不同且已实际成立——迁移前双侧已有真实重复实现: Admin 侧持有 `RoutePublishProperties` 与 Redis 发布逻辑, Gateway 侧持有另一份 `GatewayRouteProperties` 与信封解析, environment 取值一旦漂移两侧会静默读写不同 Key 且无编译期信号; 同时发布/消费 SDK 属运行设施, 不能落入 admin-api 契约边界。因此契约下沉纯契约模块 gateway-contract (供 api 引用), SDK 落 common-gateway (仅供运行进程依赖)。后续仍以真实消费者为准: 在出现第二个读侧消费者 (如运维路由查询工具) 之前, 读侧 SDK 不再拆分更细的公共包, 防止公共模块绑死 sys 业务域与 Spring Cloud Gateway 实现。

## 8. 可用性与恢复语义

- `sys_dict` 与 `sys_dict_item` 是全局表: 乐观锁 `version` 防并发覆盖, `deleted_at` 逻辑删除保证误删可追溯、可恢复, 不设租户列与租户外键; 字典项随字典物理删除时 `ON DELETE CASCADE`。
- `sys_route` 是全局表: 乐观锁与逻辑删除语义与字典一致, 误删路由可凭逻辑删除行恢复, 恢复并重新发布后即重新生效。
- `sys_config` 保持租户隔离, `tenant_id` 外键 `ON DELETE RESTRICT` 防止误删仍有参数的租户。
- 业务性 JSON 数据由具体表按语义命名专用列, 不使用通用 extension 兜底列。
- 唯一约束是幂等写入的基础, 并发创建同码字典、同值字典项或同键参数时依赖数据库约束拒绝后写方。

## 9. Schema 管理

初始迁移为 `V002__create_sys_schema.sql`, 版本号在整个 Admin Schema 内全局唯一 (UPM 已使用 `V001`)。路由表迁移为 `V003__create_sys_route.sql`, 并附带 aspen-admin 自举路由种子——Gateway 到 Admin 的首条路由无法经路由表自身发布, 由种子数据保证; `V004__align_admin_route_prefix.sql` 把种子断言对齐为 `/admin-api/**`、移除 StripPrefix, 并把目标服务名修正为注册名 `lb://aspen-admin-biz`。所有表使用 InnoDB、`utf8mb4` 和 `utf8mb4_0900_ai_ci`。后续变化采用新的前向迁移, 并遵循扩展、迁移、切换、清理顺序; 生产环境禁止依赖 Jimmer 自动建表或修改结构。

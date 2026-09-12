# Aspen Storage 数据模型

> 文档状态：首版 5 表 Entity、枚举与初始迁移已建立（含秒传唯一索引、续传唯一键与租户级业务分类树），上传/秒传/续传/合并的 Service 与对外 API 待建  
> 文档基线：2026-09-12  
> 关联文档：[技术架构](./technical-architecture.md)｜[Common 模块设计](./common-module-design.md)｜[Admin SYS 数据模型](./admin-sys-data-model.md)

## 1. 目标与来源

Aspen Storage 是全体系文件存储微服务，统一承载图片、apk、excel 等各类文件的上传、分片续传、秒传与多后端存储（本地目录 / MinIO / 阿里云 OSS / 七牛云 / 通用 S3 兼容端点），首版 5 张表，代码位于：

```text
services/aspen-storage/aspen-storage-api/src/main/kotlin/com/zax/aspen/storage/api/   # 枚举与配置参数契约
services/aspen-storage/aspen-storage-biz/src/main/kotlin/com/zax/aspen/storage/biz/entity/
services/aspen-storage/aspen-storage-biz/src/main/resources/db/migration/
```

数据按归属分为两类：`storage_config` 是平台基础设施配置，全体租户共用，不做租户隔离；`storage_category`、`storage_file`、`storage_upload_task`、`storage_upload_chunk` 是租户业务数据，保持租户隔离。Storage 使用独立 Schema `aspen_storage`，与 Admin 的 `aspen_admin` 分库，因此租户列无法跨库外键 `upm_tenant`，`tenant_id` 只作普通列与索引前缀，租户合法性由上传链路的租户上下文（common-database fail-closed 过滤器）保证。

文件的两套归类概念正交：`file_type` 是服务端按 MIME 推导的**技术形态**（图片/安装包/文档，自动、必填）；`storage_category` 是运营自建的**业务分类树**（人工、选填挂载），详见第 6 节。

多后端参考了 Friendship-and-chat-api（芋道 infra 文件服务）的策略 + 工厂 + 数据库配置热切换设计；其 `upload(byte[])` 全量内存模型、无分片续传、无去重查询、本地客户端路径穿越与 URL 耦合 configId 等问题在本模型中修正（见 5、7 节）。

## 2. 表清单

| 领域 | 表 | 职责 |
| --- | --- | --- |
| 配置 | `storage_config` | 存储后端配置：类型、连接参数、默认标记与启停状态 |
| 分类 | `storage_category` | 租户级业务分类树：编码、显示名、父子层级与启停状态 |
| 文件 | `storage_file` | 已落库文件记录：内容寻址 key、哈希、大小、MIME、技术形态、可选业务分类与访问 URL |
| 上传 | `storage_upload_task` | 分片上传任务：总量/分片规格、整文件哈希、状态机与过期时间 |
| 上传 | `storage_upload_chunk` | 分片记录：序号、字节数、分片哈希，支撑断点续传与合并 |

## 3. Jimmer 映射约定

- 映射风格与 UPM/SYS 一致：数据库自增 `BIGINT UNSIGNED` 语义化主键（表名去 `storage_` 前缀加 `_id`，如 `storage_config.config_id`）、`DATETIME(3)` 映射 `LocalDateTime`、业务默认值经 Jimmer `@Default` 与 SQL 默认值同步、列名与属性蛇形一致零 `@Column`（测试强制）、对象关联一律 `@ManyToOne` + `@JoinColumn` 双显式 + 同名 `@IdView` 三件套。
- 实体直接组合 common-database 原子：`storage_config`、`storage_category` 与 `storage_file` 组合 `MutableAuditEntity`（乐观锁 + 时间戳逻辑删除）；`storage_upload_task` 组合 `AuditableEntity` + `VersionedEntity`（可更新但由清理任务物理删除，不声明删除审计，防止行膨胀）；`storage_upload_chunk` 只组合 `CreateAuditEntity`（不可变行）。租户表叠加 `TenantScopedEntity`。
- 对象关联按数据量级区别对待：文件、任务、分片只声明引用侧（`config`/`category`/`uploadTask` 的 `@ManyToOne`），不声明反向集合——「按配置取全部文件」「按任务取全部分片」必须走条件查询与分页，不能以集合关联诱导全量加载；唯一例外是 `storage_category.children`，与 `sys_dict_item.children` 同款的有界小集合，供树形选择器级联逐级加载。`children` 经 `@OneToMany.orderedProps` 声明 `sort_order` 升序、`category_id` 兜底的确定性排序，级联加载顺序不依赖数据库返回顺序。
- `storage_category.parent` 自引用照 `sys_dict_item.parent` 惯例**不设数据库外键**，成环、跨租户挂父与移动子树的一致性由 Service 在事务内维护；子树查询走 `(tenant_id, parent_id)` 索引加 MySQL 递归 CTE。
- `storage_config.params` 是专用 JSON 列，经 Jimmer `@Serialized` 映射为 api 契约的 `StorageConfigParams`，服务层不做手工 JSON 解析。
- 状态与类型字段使用枚举：启停复用 common-core `EnabledStatus`；`StorageType`、`FileType`、`UploadTaskStatus` 为 Storage 域枚举，位于 storage-api 的 `enums` 目录（单业务边界服务不分组），持久化经 `AspenEnumProviders` 按 code 与数据库小写值互转。

## 4. 多后端存储配置

- `config_code` 全局唯一，是调用方指定目标后端的稳定引用（如「apk 固定传 main-local」），创建后不可修改，逻辑删除后同码不可重建——编码**永久占用**（对标 `sys_route`，区别于分类编码的 IFNULL 折算放行）：调用方以编码为稳定契约，且逻辑删除行仍被既有文件的 `config_id` 引用，同码重建会出现删除行与活跃行同码二义，确需同码须恢复原行而非新建；`is_default` 标记兜底后端，同一时刻只应有一个启用中的默认配置，由 Service 在事务内校验（MySQL 无法用普通唯一约束表达「条件唯一」）。未显式指定 `config_code` 的上传落默认配置。
- 多配置天然共存：本地目录、MinIO、阿里云 OSS、七牛云可同时启用，各自承接不同业务流量；`status=disabled` 的配置不参与新上传，既有文件读取不受影响。
- `StorageType` 取值 `local / minio / aliyun_oss / qiniu / s3`。minio/aliyun_oss/qiniu/s3 四类共享同一套 S3 协议客户端实现（endpoint + bucket + ak/sk + domain），枚举分开声明只为配置表单差异（如七牛强制自定义 domain、MinIO 默认 path-style）与后续按厂商扩展；`local` 走本地磁盘实现。
- `params` 采用单一扁平结构 `StorageConfigParams`（`pathBase` 仅 local 使用；`endpoint/region/bucket/accessKey/accessSecret/domain/pathStyleAccess` 仅对象存储使用），JSON 内不携带任何类型判别字段，解释权归 `storage_type` 列——避免芋道 `@class` 多态 TypeHandler 的反序列化面。必填组合由 Service 按 `StorageType` 校验。
- `accessSecret` 是敏感字段：出参 VO 必须脱敏，不得进入日志与导出；修改配置时允许「未传则不改」。
- 落参考项目「配置连通性测试」的经验：管理端保存配置后应提供 test 上传验证端点（待 API 轮实现）。

## 5. 文件与秒传设计

- 物理对象以内容寻址：`storage_key = {sha256}.{原始扩展名}`。同内容在对象存储侧天然覆盖同 key，跨租户共享物理对象；但**秒传命中判定按租户隔离**——唯一键含 `tenant_id`，租户 B 无法凭哈希探测租户 A 是否已存某内容，防跨租户内容存在性探测。
- 秒传唯一约束是本表的核心：`UNIQUE (tenant_id, sha256, config_id, IFNULL(deleted_at, '1970-01-01 00:00:00.000'))`。MySQL 唯一索引视 `NULL` 为互不相等，若直接把 `deleted_at` 入键，多行活跃记录（`deleted_at IS NULL`）不会触发唯一冲突；以 `IFNULL` 把活跃行折算为固定哨兵值后，活跃行之间真正受数据库约束保护，并发同传同内容时后写方被约束拒绝、转为秒传命中返回。已删除行以毫秒精度时间戳参与键值，同内容删除后重传不被历史行阻塞（DATETIME(3) 精度下碰撞概率可忽略）。该表达式索引要求 MySQL ≥ 8.0.13，项目基线 8.4 满足。
- 上传前置哈希检查：客户端先提交整文件 sha256，Service 以活跃行查 `(tenant_id, sha256, config_id)`，命中即返回既有 `file_id`，零字节传输。
- `url` 是冗余缓存：正典引用是 `file_id` 与 `storage_key`，domain 变更或迁移后端时按 `config_id + storage_key` 批量重算，不因配置改动断链（修正参考项目 URL 内嵌 configId 的耦合）。
- `file_type` 由服务端按 MIME 推导（image/video/audio/document/apk/archive/other），供管理端筛选与统计；不参与存储路由判断。`category_id` 可空，业务分类挂载语义见第 6 节。
- 文件记录逻辑删除只作用于记录行；物理对象回收依赖「同 key 活跃记录计数」，由后续清理任务在统一任务服务接入后执行，删除记录不立即删对象。该计数查询跨全租户（物理对象按内容寻址共享，`WHERE storage_key = ? AND deleted_at IS NULL`），走 `idx_storage_file_storage_key (storage_key)` 索引。

## 6. 业务分类设计

- 概念定位：`storage_category` 表达运营视角的业务归类（如「营销物料/商品图」「交付物/合同」），与 `file_type` 技术形态正交；挂载是**选填**的——`storage_file.category_id` 可空，NULL 即未分类，不设「默认分类」行（避免删除/停用默认分类的语义死角）。
- 分类是租户业务数据：各租户自建自管分类树，不复用 Admin 的 `sys_dict`（全局表 + 跨库不可达，违反服务数据自治）。
- 树模型为邻接表：`parent_id` 自引用、根节点为空、不设数据库外键；子树查询（按分类及其后代筛文件）用 MySQL 递归 CTE 配合 `(tenant_id, parent_id)` 索引。文件分类层级浅、子树查询低频，不引入 UPM 部门闭包表那种为深层级高频子树权限查询付出的写放大。
- `category_code` 同租户唯一（IFNULL 哨兵唯一键，与秒传键同款语义），供上传方稳定引用，创建后不可修改、逻辑删除后可重建同码——分类是运营会反复建删的数据，不走 `sys_route` 的「编码永久占用」语义；`category_name` 随时改不影响已挂载文件。
- 文件可挂载到树中**任意节点**（不限叶子）：叶子在运营调整后可能变成中间节点，限叶子挂载会让历史挂载失效；任意节点挂载配合子树查询（含后代）即可覆盖「按大类筛文件」的诉求。
- 树一致性由 Service 强制：创建/移动节点时校验父节点同租户且启用中、目标父级不是自身后代（防成环）；删除分类的前置校验为「无未删除子分类且无活跃文件挂载」，需先移动子树与文件再删除，防止悬挂引用。`status=disabled` 的分类不出现在选择器、禁止新文件挂载，已挂载文件不受影响。

## 7. 分片上传与断点续传

- 链路（待 API 轮实现）：初始化任务（携带文件名、总大小、分片大小、整文件 sha256，返回 `upload_task_id`，顺带执行秒传检查）→ 逐片上传 → 客户端续传前按任务查询已收分片号列表 → 全部到齐后合并落后端、生成 `storage_file` 并置任务完成。
- `uk_storage_upload_chunk_task_number (upload_task_id, chunk_number)` 是断点续传的核心：已收分片号集合即「任务 + 唯一键存在性」查询；重复上传同一分片按幂等覆盖处理。分片号从 1 起，连续编号到 `total_chunks`。
- 状态机 `uploading → merging → completed`，任意阶段可 `failed / canceled`；合并即服务端流式拼接分片写入目标后端，校验拼接结果 sha256 与 `file_sha256` 一致才落文件记录。V1 分片统一经服务端中转（分片落本地临时目录），LOCAL 与 S3 系语义一致；`remote_upload_id`（S3 multipart uploadId）与 `remote_part_tag`（part ETag）为二期「S3 原生分片直传」预留，V1 恒为 NULL。
- `total_size / chunk_size / total_chunks` 三元组在初始化时固化，分片校验「末片允许小于 chunk_size、其余必须等于」的规格由 Service 强制；`file_sha256` 同时是秒传预检键与合并完整性校验值，`chunk_sha256` 可选由客户端提供用于单片完整性校验。
- 过期治理：任务初始化时写 `expires_at`（按策略配置，默认未完成任务保留 24 小时），完成后置 NULL。清理任务物理删除过期未完成任务及其分片（外键 `ON DELETE CASCADE` 连带分片）；本表不声明逻辑删除。清理是**跨租户的系统级补偿扫描**（技术架构 14.1.2「分页或分片扫描」）：统一任务服务投递的清理命令不携带租户等值条件，按系统上下文分页扫描 `task_status IN (...) AND expires_at < now`，索引为 `(task_status, expires_at)`（不含租户前缀）；租户过滤器 fail-closed，清理链路须以显式系统上下文执行（具体机制随任务服务落地定义）；统一任务服务接入前以手动运维脚本按同款路径兜底。
- 任务表可重复创建：同一文件重试、续传超期后重建任务都是新行，`upload_task_id` 不复用。

## 8. 可用性与恢复语义

- `storage_config`、`storage_category` 与 `storage_file` 是乐观锁 `version` + `deleted_at` 逻辑删除：配置与分类误删可恢复（恢复时须处理 is_default/编码唯一性），文件误删恢复后重新参与秒传命中；分类删除后同码可重建，配置编码永久占用、删除后不可重建同码（见第 4 节）。
- `storage_upload_task` 乐观锁保护状态机并发迁移（合并中禁止重复触发合并）；任务与分片是过程数据，物理删除、不回收站。
- 外键策略：`storage_file.config_id`、`storage_file.category_id`、`storage_upload_task.config_id` 对被引表 `ON DELETE RESTRICT`（有存量的配置/分类必须先迁移文件才能清理）；`storage_upload_chunk.upload_task_id` 对任务 `ON DELETE CASCADE`（任务清理连带分片）；`storage_category.parent_id` 不设外键（邻接表树一致性由 Service 维护）。
- `tenant_id` 不设数据库外键（跨库不可行），租户行过滤由 common 租户过滤器强制，fail-closed。
- 唯一约束是幂等写入的基础：并发同传同内容依赖 `uk_storage_file_dedup` 拒绝后写方转为秒传；并发建同码分类依赖 `uk_storage_category_tenant_code` 拒绝后写方；并发补传同号分片依赖 `uk_storage_upload_chunk_task_number` 拒绝后写方按幂等覆盖处理。

## 9. Schema 管理

初始迁移为 `V001__create_storage_schema.sql`，位于 `db/migration/` 根（单业务域不按组分子目录），建表顺序为 config → category → file → task → chunk（满足外键依赖）。Storage 的版本序列独立于 Admin（Admin 已用 V001–V004），服务内全局唯一、只增不改。所有表使用 InnoDB、`utf8mb4` 和 `utf8mb4_0900_ai_ci`。后续变化采用新的前向迁移，遵循扩展、迁移、切换、清理顺序；生产环境禁止依赖 Jimmer 自动建表或修改结构。

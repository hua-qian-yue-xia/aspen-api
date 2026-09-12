# Aspen 文档目录

> 本目录是 Aspen 全部设计文档的唯一入口。每篇文档只覆盖一个重点方向，主题的权威定义唯一，不在多篇文章中重复展开。

## 1. 文档清单与职责边界

| 文档 | 重点方向 | 回答的问题 | 不覆盖的内容 |
| --- | --- | --- | --- |
| [project-goals.md](./project-goals.md) | 项目定位与目标定义 | 为什么建设 Aspen、达到什么标准算完成 | 不包含技术选型和代码结构 |
| [microservice-technical-solution.md](./microservice-technical-solution.md) | 首期技术选型与组件方案 | 用什么技术栈、每个中间件组件的职责、失败策略与实施顺序 | 不定义工程目录和包结构规则 |
| [technical-architecture.md](./technical-architecture.md) | 工程结构与代码组织规范 | 模块怎么拆、api/biz 边界、目录与命名、依赖方向、架构测试与验收 | 不重复组件选型理由，只引用技术方案结论 |
| [common-module-design.md](./common-module-design.md) | 公共基础模块 API 细节 | core/database/cache 提供哪些类型和配置键、如何接入与验收 | 不包含业务服务和业务数据设计 |
| [admin-upm-data-model.md](./admin-upm-data-model.md) | Admin UPM 数据模型 | UPM 有哪些表、Jimmer 映射约定、租户唯一性与安全字段边界 | 不包含 Controller/Service 等运行实现设计 |
| [admin-sys-data-model.md](./admin-sys-data-model.md) | Admin SYS 数据模型 | SYS 字典、参数与路由表结构、内置与敏感标记、可用性与分发语义 | 不包含 Controller/Service 等运行实现设计 |
| [storage-data-model.md](./storage-data-model.md) | Storage 文件存储数据模型 | 文件存储 4 表结构、多后端配置、秒传与分片续传语义、过期清理约定 | 不包含上传 API、FileClient 实现与对象回收等运行实现设计 |
| [task-data-model.md](./task-data-model.md) | Task 统一任务数据模型 | 任务定义/租户圈定/执行记录表结构、Quartz 集群与对账语义、HTTP 投递与 SSRF 防护约定 | 不包含 RBAC、消息通道投递与告警通知等运行实现设计 |
| [auth-data-model.md](./auth-data-model.md) | 认证数据模型 | 端注册/端×登录方式/主体凭据三层模型、令牌与会话语义、Principal SPI 契约、配置分发 | 不包含网关过滤器实现与业务服务接入细节 |
| [dev-deployment.md](./dev-deployment.md) | 开发环境部署与 Nacos 配置中心 | 本地 compose 怎么起、Nacos 鉴权与种子机制、dataId 分层、客户端接线 | 不定义生产拓扑与备份恢复策略（见技术方案） |
| [roles/code-reviewer.md](./roles/code-reviewer.md) | 代码审查官角色设定 | 代码提交审查时扮演什么角色、按什么清单审查、意见如何分级输出 | 不定义架构规范本身，只引用《技术架构》条款 |

## 2. 推荐阅读顺序

1. **[项目目标](./project-goals.md)**：先理解 Aspen 是微服务基础设施，不是业务系统，并明确规模与 SLO 目标。
2. **[微服务技术方案](./microservice-technical-solution.md)**：确认首期技术栈和各组件的职责边界。
3. **[技术架构](./technical-architecture.md)**：掌握 api/biz 拆分、目录规范、依赖规则和架构测试基线，这是写代码前必读的一篇。
4. **[Common 模块设计](./common-module-design.md)**：开发中需要错误码、分页、Jimmer 基类或 Redis 缓存封装时查阅。
5. **[Admin UPM 数据模型](./admin-upm-data-model.md)**：参与 Admin UPM 持久化开发时查阅。
6. **[Admin SYS 数据模型](./admin-sys-data-model.md)**：参与 Admin SYS 字典与参数持久化开发时查阅。
7. **[Storage 数据模型](./storage-data-model.md)**：参与文件存储服务持久化开发时查阅。
8. **[Task 数据模型](./task-data-model.md)**：参与统一任务服务（调度、投递、执行记录）开发时查阅。
9. **[认证数据模型](./auth-data-model.md)**：参与认证服务、端配置或 Principal SPI 开发时查阅。

## 3. 主题权威规则

为避免内容重复和漂移，以下主题只在唯一权威文档中维护，其他文档仅链接引用：

| 主题 | 权威位置 |
| --- | --- |
| 容量目标、SLO 与完成定义 | [project-goals.md](./project-goals.md) |
| 组件选型结论与版本基线 | [microservice-technical-solution.md](./microservice-technical-solution.md) 第 4 节 |
| Gradle 模块结构、api/biz 目录与命名规范 | [technical-architecture.md](./technical-architecture.md) 第 6、7 节 |
| Admin 复合服务分组与表所有权 | [technical-architecture.md](./technical-architecture.md) 7.10 节 |
| 统一任务服务 Quartz 语义 | [technical-architecture.md](./technical-architecture.md) 14.1 节 |
| 公共模块类型、配置键与缓存操作语义 | [common-module-design.md](./common-module-design.md) |
| UPM 表结构与迁移约定 | [admin-upm-data-model.md](./admin-upm-data-model.md) |
| SYS 字典、参数与路由表结构 | [admin-sys-data-model.md](./admin-sys-data-model.md) |
| 文件存储表结构、多后端配置与秒传/续传语义 | [storage-data-model.md](./storage-data-model.md) |
| 任务表结构、租户圈定与 HTTP 投递语义 | [task-data-model.md](./task-data-model.md) |
| 认证三层模型、令牌会话语义与 Principal SPI | [auth-data-model.md](./auth-data-model.md) |
| 开发环境 compose、Nacos 鉴权/种子与客户端接线 | [dev-deployment.md](./dev-deployment.md) |

新增文档时必须先在本目录登记职责边界；与既有文档方向重叠的内容应合并进权威文档，而不是新建一篇相似主题。

## 4. 文档维护约定

- 每篇文档头部标注文档状态、基线日期和关联文档；结构性变更需同步更新基线日期。
- 目标架构与当前仓库实现必须区分表述，规划能力不得描述为已实现。
- 文档中的目录树、包名和规则与代码不一致时，先修订文档并同步代码，保持两者可对照。

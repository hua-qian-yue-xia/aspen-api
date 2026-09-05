# Admin UPM 数据模型

> 文档状态：首版 24 张表 Entity 与版本化 Schema 已实现，Controller/Service 待建  
> 文档基线：2026-09-06  
> 关联文档：[技术架构](./technical-architecture.md)｜[Common 模块设计](./common-module-design.md)

## 1. 目标与来源

Admin UPM 使用 Jimmer 建模，并以 `aspen-node/modules/upm/src/infrastructure/database/schema` 的字段、约束和索引为兼容基线。当前实现覆盖 24 张表，代码位于：

```text
services/aspen-admin/aspen-admin-biz/src/main/kotlin/com/zax/aspen/admin/biz/entity/upm/
services/aspen-admin/aspen-admin-biz/src/main/resources/db/migration/upm/
```

本轮只建立 Entity 与版本化 Schema，不提前创建 Controller、Service、Repository、API DTO 或查询对象。实体属于 Admin Biz 内部持久化模型，禁止通过 HTTP、Feign 或消息契约直接暴露。

## 2. 表清单

| 领域 | 表 | 职责 |
| --- | --- | --- |
| 租户 | `upm_tenant` | 租户、默认区域、有效期和租户级配置 |
| 用户 | `upm_user` | 用户身份、联系方式、登录状态和主部门冗余 |
| 用户 | `upm_user_credential` | 密码等可轮换凭证摘要 |
| 用户 | `upm_user_identity` | OIDC、SAML、LDAP 等外部身份绑定 |
| 用户 | `upm_user_dept` | 用户任职、兼职和主管关系 |
| 部门 | `upm_dept` | 部门邻接树、属性和统计冗余 |
| 部门 | `upm_dept_closure` | 部门祖先和后代闭包路径 |
| 部门 | `upm_dept_leader` | 部门多类型负责人关系 |
| 角色 | `upm_role` | 角色、默认数据范围和权限版本 |
| 角色 | `upm_user_role` | 用户角色授权、范围、周期和撤销轨迹 |
| 角色 | `upm_role_dept` | 角色自定义部门范围 |
| 角色 | `upm_role_inheritance` | 角色继承闭包路径 |
| 菜单 | `upm_menu` | 目录、页面、按钮和外部链接路由 |
| 菜单 | `upm_role_menu` | 角色到菜单的导航授权 |
| 权限 | `upm_permission` | 资源、动作和风险等级定义 |
| 权限 | `upm_permission_api` | 权限到 HTTP API 路由映射 |
| 权限 | `upm_role_permission` | 角色到后端权限授权 |
| 权限 | `upm_menu_permission` | 菜单或按钮到后端权限映射 |
| 权限 | `upm_user_permission` | 用户直接允许或拒绝某项权限 |
| 安全 | `upm_user_session` | 刷新令牌摘要、设备和会话生命周期 |
| 安全 | `upm_user_mfa` | TOTP、WebAuthn 等多因素认证方式 |
| 安全 | `upm_password_history` | 密码历史摘要 |
| 审计 | `upm_login_log` | 不可变登录审计记录 |
| 审计 | `upm_authorization_change_log` | 不可变授权变更快照 |

## 3. Jimmer 映射约定

- 实体以 `Entity` 结尾，每个 Jimmer Entity 或 MappedSuperclass 单独放在一个 Kotlin 文件中。
- 主键映射为数据库 `BIGINT UNSIGNED AUTO_INCREMENT`，JVM 使用 `Long`。主键列使用表名去掉 `upm_` 前缀加 `_id` 的语义化命名（如 `upm_user.user_id`），由各 Entity 自行声明，公共映射不固定 ID 策略；引用列与被引用主键同名，join 语义自解释。将来增加对象关联时必须显式声明被引用主键列，不依赖默认 `id` 约定；写入值必须限制在 JVM 有符号 `Long` 范围内。
- 除 `upm_tenant` 外的业务表包含 `tenant_id`；租户条件必须由未来 Repository 的每条业务查询显式约束。
- 首版外键在 Jimmer 中映射为标量 ID，数据库迁移仍保留完整外键。等真实查询形状确定后再按需增加关联，避免默认加载对象图。
- `DATETIME(3)` 映射为 `LocalDateTime`，部署域全部 Docker 主机与应用进程统一使用 `Asia/Shanghai` 时区并接入统一 NTP，时间按服务器时区直接存取；租户 `timezone` 只用于跨时区展示转换。
- 实体直接组合 common 的 `MutableAuditEntity`（可更新表：完整审计、初始值为 `1` 的乐观锁、`deleted_at` 时间戳逻辑删除）或 `CreateAuditEntity`（不可变关系表：`created_at` 与 `created_by`）与 `TenantScopedEntity`（租户列），不创建业务包级纯组合接口。主键由各实体自行声明，公共映射不固定 ID 策略。租户条件由 common 的 `TenantFilter` 自动追加，业务查询不手写 `tenant_id`。
- SQL 中的业务默认值通过 Jimmer `@Default` 同步，创建时间和发生时间使用 `@Default("now")` 生成部署域统一时区的 `LocalDateTime`；SQL 同时保留 `CURRENT_TIMESTAMP(3)`，保证非 Jimmer 写入也有数据库默认时间。Jimmer `0.11.7` 的 Kotlin 元数据在校验 `@DatabaseDefault` 时存在数组类型转换缺陷，本版不使用该注解。
- 关系和历史表只保留 `created_at`、`created_by`；登录日志和授权变更日志不支持更新或逻辑删除。
- 状态、来源、授权效果等字段首版使用 `String`，以保持数据库中的小写值不被 JVM 枚举名称改写。引入枚举时必须显式定义并测试持久化值转换。

## 4. 租户与唯一性

租户内业务编码、用户名、规范化邮箱、规范化手机号、角色、菜单和权限都使用包含 `tenant_id` 的唯一约束。`upm_tenant.tenant_code` 在整个部署内唯一，`domain` 允许为空。

MySQL 的唯一索引遇到 `NULL` 时允许多条记录，因此下列来源兼容约束不能单独保证空值场景的业务唯一性：

- `upm_user_role(scope_dept_id)`
- `upm_permission_api(api_version)`
- 其他包含可空邮箱、手机号、外部标识或凭证标识的唯一索引

本版保留 Node Schema 行为，不引入生成列改变兼容性。未来 Service/Repository 写入流程必须明确空值标准化规则，并在需要严格唯一时通过新的版本化迁移处理。

## 5. 组织、角色与菜单

部门同时保存 `parent_id` 邻接关系、`ancestor_path` 和 `upm_dept_closure`。邻接关系用于直接父子维护，闭包表用于祖先、后代和数据范围查询；任何移动部门操作都必须在一个本地事务内同步重建路径和闭包记录。

角色继承使用 `upm_role_inheritance` 闭包表，必须包含自身 `depth = 0` 的路径，并在新增继承关系前拒绝环。角色菜单只决定导航可见性，后端访问仍以 `upm_permission`、API 映射和最终授权计算为准，不能把菜单可见等同于接口有权访问。

## 6. 安全边界

UPM 拥有凭证摘要、外部身份、MFA、会话、密码历史和登录审计的权威持久化数据。Auth 拥有登录协议、认证编排、Token 签发与刷新、客户端认证、服务身份和密钥生命周期。Auth 必须通过 Admin UPM 契约访问这些状态，不直接连接 Admin Schema。

安全字段遵守以下要求：

- `secret_hash`、`password_hash` 和 `refresh_token_hash` 只能保存不可逆摘要，不保存原值。
- `encrypted_secret` 必须在应用层使用受控密钥加密后写入，不能保存 TOTP 明文密钥。
- 日志和异常禁止输出上述字段、原始 Token、完整凭证或身份快照中的敏感内容。
- `upm_login_log` 和 `upm_authorization_change_log` 只追加，不提供普通更新与删除流程。

## 7. Schema 管理

初始迁移为 `V001__create_upm_schema.sql`。版本号在整个 Admin Schema 内必须全局唯一，即使迁移按 `upm/sys` 子目录组织也不能重复。生产环境禁止依赖 Jimmer 自动建表或自动修改结构，后续变化采用新的前向迁移，并遵循扩展、迁移、切换、清理顺序。

所有表使用 InnoDB、`utf8mb4` 和 `utf8mb4_0900_ai_ci`。租户引用使用 `ON DELETE RESTRICT`；纯关系记录通常随主体级联删除；授权人、撤销人、主管和可选范围等历史引用使用 `ON DELETE SET NULL`。`parent_id`、用户主部门和部分审计主体 ID 按来源设计保留为无数据库外键的标量引用。

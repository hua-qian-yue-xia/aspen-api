package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存权限到 HTTP API 路由的映射
 *
 * 典型场景: Gateway 与公共安全过滤链按请求路径反查所需权限, 完成接口级鉴权
 */
@Entity
@Table(name = "upm_permission_api")
interface UpmPermissionApiEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val permissionApiId: Long

    /** 权限主键; 一个权限可映射多条路由, 一条路由只属于一个权限 */
    val permissionId: Long

    /** 路由归属应用标识, 例如 admin-api/order-api; 跨服务路由归属各自应用 */
    @Default("admin-api")
    val application: String

    /** HTTP 方法, 大写形式, 例如 GET/POST; 配合 pathPattern 精确匹配 */
    val httpMethod: String

    /** 路由模式, 支持 Ant 风格通配, 例如 /api/v1/users 加通配符; 匹配顺序由 Service 保证精确优先 */
    val pathPattern: String

    /** API 版本号, 例如 v1; 同一路径多版本时区分; 可空表示不限版本 */
    val apiVersion: String?

    /** 映射状态; disabled 后该路由鉴权按未配置处理, 由安全策略决定放行或拒绝 */
    @Default("enabled")
    val status: String
}

package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存资源动作形式的后端权限定义
 *
 * 典型场景: 后端接口鉴权与前端按钮控制的权限点来源;
 * 权限以「资源 + 动作」二维定义, 例如 user:delete
 */
@Entity
@Table(name = "upm_permission")
interface UpmPermissionEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val permissionId: Long

    /** 权限编码, 全租户唯一, 形如 user:delete; 业务系统与前端指令以此引用, 创建后不可修改 */
    val permissionCode: String

    /** 权限名称, 管理界面展示与搜索 */
    val name: String

    /** 权限类型, 约定取值为 api/button/data; api 由后端拦截器校验, button 由前端指令控制 */
    @Default("api")
    val permissionType: String

    /** 权限指向的资源, 例如 user、order; 与 action 组成权限语义 */
    val resource: String

    /** 资源上的动作, 例如 create/read/update/delete/export */
    val action: String

    /** 风险等级, 约定取值为 normal/high/critical; high 及以上触发二次确认或审批 */
    @Default("normal")
    val riskLevel: String

    /** 强制审计标记; 使用该权限的操作必须写入授权变更日志 */
    @Default("false")
    val requiresAudit: Boolean

    /** 权限状态; disabled 后授权关系保留但鉴权全部拒绝 */
    @Default("enabled")
    val status: String

    /** 权限用途说明, 描述典型使用场景与影响范围 */
    val description: String?
}

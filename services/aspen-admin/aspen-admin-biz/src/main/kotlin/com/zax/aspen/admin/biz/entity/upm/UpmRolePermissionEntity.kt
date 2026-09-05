package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存角色到后端权限的授权关系
 *
 * 典型场景: 角色权限计算的来源之一; 与用户直接授权、菜单权限映射合并出最终权限集
 */
@Entity
@Table(name = "upm_role_permission")
interface UpmRolePermissionEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val rolePermissionId: Long

    /** 授权角色主键 */
    val roleId: Long

    /** 授权权限主键 */
    val permissionId: Long

    /** 授权来源, 约定取值为 manual/template */
    @Default("manual")
    val grantSource: String

    /** 授权生效起点; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 授权失效终点; 到期后该权限不再生效; 为空表示长期有效 */
    val validTo: LocalDateTime?
}

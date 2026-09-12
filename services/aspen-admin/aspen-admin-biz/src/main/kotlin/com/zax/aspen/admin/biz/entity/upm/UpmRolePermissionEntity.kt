package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
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

    /** 授权角色; 角色删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id")
    val role: UpmRoleEntity

    /** 授权角色主键; role 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("role")
    val roleId: Long

    /** 授权权限; 权限删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "permission_id", referencedColumnName = "permission_id")
    val permission: UpmPermissionEntity

    /** 授权权限主键; permission 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("permission")
    val permissionId: Long

    /** 授权来源, 约定取值为 manual/template */
    @Default("manual")
    val grantSource: String

    /** 授权生效起点; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 授权失效终点; 到期后该权限不再生效; 为空表示长期有效 */
    val validTo: LocalDateTime?
}

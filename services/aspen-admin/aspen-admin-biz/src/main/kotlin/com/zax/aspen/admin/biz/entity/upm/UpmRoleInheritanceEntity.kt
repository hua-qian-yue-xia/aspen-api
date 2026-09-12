package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table

/**
 * 保存角色继承闭包路径以支持多层权限组合
 *
 * 典型场景: 角色继承权限传递; 新增继承关系前必须拒绝环;
 * 每对父子角色恰好一行, 自身 depth 为 0 的行由建立继承时生成
 */
@Entity
@Table(name = "upm_role_inheritance")
interface UpmRoleInheritanceEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val roleInheritanceId: Long

    /** 父角色; 父角色的权限向子角色传递 */
    @ManyToOne
    @JoinColumn(name = "parent_role_id", referencedColumnName = "role_id")
    val parentRole: UpmRoleEntity

    /** 父角色主键; parentRole 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("parentRole")
    val parentRoleId: Long

    /** 子角色 */
    @ManyToOne
    @JoinColumn(name = "child_role_id", referencedColumnName = "role_id")
    val childRole: UpmRoleEntity

    /** 子角色主键; childRole 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("childRole")
    val childRoleId: Long

    /** 传递跳数; 自身为 0, 直接继承为 1; 展开多层继承时使用 */
    val depth: Int
}

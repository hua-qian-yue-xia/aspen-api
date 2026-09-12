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

/**
 * 保存角色自定义部门数据范围
 *
 * 典型场景: 角色 dataScope 为 custom 时, 以本表展开该角色可访问的部门清单
 */
@Entity
@Table(name = "upm_role_dept")
interface UpmRoleDeptEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val roleDeptId: Long

    /** 自定义范围的角色; 角色删除时级联删除范围行 */
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id")
    val role: UpmRoleEntity

    /** 角色主键; role 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("role")
    val roleId: Long

    /** 范围部门; 部门删除时级联删除范围行 */
    @ManyToOne
    @JoinColumn(name = "dept_id", referencedColumnName = "dept_id")
    val dept: UpmDeptEntity

    /** 范围部门主键; dept 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("dept")
    val deptId: Long

    /** 包含下级部门标记; true 时数据范围通过闭包表展开到该部门的全部后代 */
    @Default("true")
    val includeChildren: Boolean
}

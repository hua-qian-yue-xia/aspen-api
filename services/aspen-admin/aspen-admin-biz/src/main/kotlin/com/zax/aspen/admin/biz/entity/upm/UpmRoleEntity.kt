package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存角色定义、默认数据范围和权限缓存版本
 *
 * 典型场景: 角色管理与授权; 用户的实际权限由角色、直接授权与拒绝项合并计算
 */
@Entity
@Table(name = "upm_role")
interface UpmRoleEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val roleId: Long

    /** 角色编码, 租户内唯一; 业务系统以编码引用角色, 创建后不可修改 */
    val roleCode: String

    /** 角色名称, 管理界面展示与搜索 */
    val name: String

    /** 角色类型, 约定取值为 business/system/api 等; 系统类型不参与用户分配 */
    @Default("business")
    val roleType: String

    /** 角色归属部门; 部门管理员只能分配本部门拥有的角色; 为空表示租户级角色; 归属部门被删除时置空 */
    @ManyToOne
    @JoinColumn(name = "owner_dept_id", referencedColumnName = "dept_id")
    val ownerDept: UpmDeptEntity?

    /** 归属部门主键; ownerDept 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("ownerDept")
    val ownerDeptId: Long?

    /** 默认数据范围, 约定取值为 self/dept/dept_and_children/custom/all; custom 时按 upm_role_dept 展开 */
    @Default("self")
    val dataScope: String

    /** 内置角色标记; 系统运行依赖的角色禁止删除, 例如租户管理员 */
    @Default("false")
    val isBuiltIn: Boolean

    /** 可分配标记; false 时普通管理员不能把该角色授予用户, 仅系统自动授予 */
    @Default("true")
    val isAssignable: Boolean

    /** 角色优先级; 多角色数据范围冲突时取大者; 数值越大优先级越高 */
    @Default("0")
    val priority: Int

    /** 角色权限缓存版本; 角色权限变更时递增, 用于失效引用该角色的授权缓存 */
    @Default("1")
    val permissionVersion: Int

    /** 角色状态; disabled 后该角色的授权整体失效 */
    @Default("ENABLED")
    val status: EnabledStatus

    /** 角色有效期起点; 授权时校验; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 角色有效期终点; 到期后授权失效; 为空表示长期有效 */
    val validTo: LocalDateTime?

    /** 角色描述, 说明角色的用途与使用范围 */
    val description: String?

    /** 被授予本角色的用户授权; 用户权限计算与角色成员管理使用 */
    @OneToMany(mappedBy = "role")
    val userRoles: List<UpmUserRoleEntity>

    /** 本角色的自定义部门数据范围; dataScope 为 custom 时展开使用 */
    @OneToMany(mappedBy = "role")
    val roleDepts: List<UpmRoleDeptEntity>

    /** 本角色授权的菜单; 角色菜单树渲染使用 */
    @OneToMany(mappedBy = "role")
    val roleMenus: List<UpmRoleMenuEntity>

    /** 本角色授权的后端权限; 角色权限计算使用 */
    @OneToMany(mappedBy = "role")
    val rolePermissions: List<UpmRolePermissionEntity>

    /** 本角色作为父角色的继承行, 即权限向哪些子角色传递; 环校验与继承展开使用 */
    @OneToMany(mappedBy = "parentRole")
    val parentRoleInheritances: List<UpmRoleInheritanceEntity>

    /** 本角色作为子角色的继承行, 即权限继承自哪些父角色; 权限合并计算使用 */
    @OneToMany(mappedBy = "childRole")
    val childRoleInheritances: List<UpmRoleInheritanceEntity>
}

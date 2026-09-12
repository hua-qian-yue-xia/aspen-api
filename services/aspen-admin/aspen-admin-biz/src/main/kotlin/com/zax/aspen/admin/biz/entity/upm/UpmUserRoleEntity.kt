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
 * 保存用户角色授权范围、来源、周期和撤销轨迹
 *
 * 典型场景: 用户权限计算的数据源; 撤销不物理删除而是记录撤销人与时间, 供审计追溯
 */
@Entity
@Table(name = "upm_user_role")
interface UpmUserRoleEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userRoleId: Long

    /** 被授权用户; 用户或角色删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 被授权用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 授权角色; 角色删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id")
    val role: UpmRoleEntity

    /** 授权角色主键; role 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("role")
    val roleId: Long

    /** 数据范围覆盖部门; 为空表示使用角色默认数据范围; 非空时仅在该部门范围内生效; 范围部门被删除时置空 */
    @ManyToOne
    @JoinColumn(name = "scope_dept_id", referencedColumnName = "dept_id")
    val scopeDept: UpmDeptEntity?

    /** 数据范围覆盖部门主键; scopeDept 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("scopeDept")
    val scopeDeptId: Long?

    /** 授权来源, 约定取值为 manual/import/api, 区分管理员手工与系统批量授权 */
    @Default("manual")
    val grantSource: String

    /** 授权状态; revoked 表示已撤销; enabled 之外的状态不参与权限计算 */
    @Default("enabled")
    val status: String

    /** 授权生效起点; 支持未来生效的预授权; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 授权失效终点; 到期自动失效; 为空表示长期有效 */
    val validTo: LocalDateTime?

    /** 执行授权的用户; 系统自动授权时为空; 授权人被删除时置空 */
    @ManyToOne
    @JoinColumn(name = "granted_by", referencedColumnName = "user_id")
    val grantedByUser: UpmUserEntity?

    /** 授权用户主键; grantedByUser 关联的标量视图, 审计追溯与保存时使用 */
    @IdView("grantedByUser")
    val grantedBy: Long?

    /** 授权原因, 例如「入职默认角色」「项目临时授权」; 审计与到期清理判断使用 */
    val grantReason: String?

    /** 撤销时间; 为空表示未撤销; 设置后授权失效但记录保留 */
    val revokedAt: LocalDateTime?

    /** 执行撤销的用户; 撤销人被删除时置空 */
    @ManyToOne
    @JoinColumn(name = "revoked_by", referencedColumnName = "user_id")
    val revokedByUser: UpmUserEntity?

    /** 撤销用户主键; revokedByUser 关联的标量视图, 审计追溯与保存时使用 */
    @IdView("revokedByUser")
    val revokedBy: Long?

    /** 撤销原因; 审计与误撤销恢复判断使用 */
    val revokeReason: String?
}

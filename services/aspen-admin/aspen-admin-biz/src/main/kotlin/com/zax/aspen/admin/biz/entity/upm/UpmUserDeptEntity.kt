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
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存用户在部门中的任职、兼职和主管关系
 *
 * 典型场景: 组织通讯录、数据权限按部门展开; 一个用户可在多个部门任职, 其中一条为主任职
 */
@Entity
@Table(name = "upm_user_dept")
interface UpmUserDeptEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val userDeptId: Long

    /** 任职用户; 用户物理删除时级联删除任职 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 任职用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 任职部门; 部门删除时级联删除任职 */
    @ManyToOne
    @JoinColumn(name = "dept_id", referencedColumnName = "dept_id")
    val dept: UpmDeptEntity

    /** 任职部门主键; dept 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("dept")
    val deptId: Long

    /** 主任职标记; 每个用户只有一条主任职, 与用户表 primaryDeptId 保持一致 */
    @Default("false")
    val isPrimary: Boolean

    /** 该任职的头衔; 同一用户在不同部门可以有不同头衔 */
    val positionTitle: String?

    /** 用工类型, 例如正式/实习/外包; 通讯录筛选与权限策略使用 */
    val employeeType: String?

    /** 直属上级; 审批流默认审批人与组织树展示使用; 为空表示无上级; 用户被删除时置空 */
    @ManyToOne
    @JoinColumn(name = "manager_user_id", referencedColumnName = "user_id")
    val managerUser: UpmUserEntity?

    /** 直属上级主键; managerUser 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("managerUser")
    val managerUserId: Long?

    /** 入职时间; 组织报表统计使用 */
    val joinedAt: LocalDateTime?

    /** 离职时间; 设置后该任职不再参与通讯录与数据授权 */
    val leftAt: LocalDateTime?

    /** 任职状态; enabled 参与组织与授权, disabled 保留历史 */
    @Default("ENABLED")
    val status: EnabledStatus

    /** 同部门内的展示顺序 */
    @Default("0")
    val sortOrder: Int
}

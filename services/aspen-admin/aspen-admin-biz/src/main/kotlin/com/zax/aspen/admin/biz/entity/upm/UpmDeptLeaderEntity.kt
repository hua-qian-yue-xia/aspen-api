package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
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
 * 保存部门的多类型负责人及其生效周期
 *
 * 典型场景: 一个部门同时有行政负责人、业务负责人等多种角色;
 * 部门表只冗余第一负责人, 完整负责人关系以本表为准
 */
@Entity
@Table(name = "upm_dept_leader")
interface UpmDeptLeaderEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val deptLeaderId: Long

    /** 负责人所在部门; 部门删除时级联删除负责人关系 */
    @ManyToOne
    @JoinColumn(name = "dept_id", referencedColumnName = "dept_id")
    val dept: UpmDeptEntity

    /** 部门主键; dept 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("dept")
    val deptId: Long

    /** 负责人用户; 用户物理删除时级联删除负责人关系 */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    val user: UpmUserEntity

    /** 负责人用户主键; user 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("user")
    val userId: Long

    /** 负责人类型, 约定取值为 primary/deputy/business 等, 同部门同类型唯一 */
    @Default("primary")
    val leaderType: String

    /** 负责人生效起点; 为空表示立即生效; 支持任期交接的重叠期 */
    val validFrom: LocalDateTime?

    /** 负责人生效终点; 到期自动失效; 为空表示长期担任 */
    val validTo: LocalDateTime?

    /** 同类型多负责人时的优先顺序 */
    @Default("0")
    val sortOrder: Int

    /** 任职状态; disabled 保留历史记录供审计 */
    @Default("ENABLED")
    val status: EnabledStatus
}

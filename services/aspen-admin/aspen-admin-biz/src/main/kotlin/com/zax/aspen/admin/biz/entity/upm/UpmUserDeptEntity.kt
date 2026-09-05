package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存用户在部门中的任职、兼职和主管关系 */
@Entity
@Table(name = "upm_user_dept")
interface UpmUserDeptEntity : UpmMutableEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "dept_id")
    val deptId: Long

    @Column(name = "is_primary")
    @Default("false")
    val isPrimary: Boolean

    @Column(name = "position_title")
    val positionTitle: String?

    @Column(name = "employee_type")
    val employeeType: String?

    @Column(name = "manager_user_id")
    val managerUserId: Long?

    @Column(name = "joined_at")
    val joinedAt: Instant?

    @Column(name = "left_at")
    val leftAt: Instant?

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "sort_order")
    @Default("0")
    val sortOrder: Int
}

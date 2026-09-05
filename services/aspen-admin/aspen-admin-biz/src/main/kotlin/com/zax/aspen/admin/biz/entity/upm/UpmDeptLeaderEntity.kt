package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存部门的多类型负责人及其生效周期 */
@Entity
@Table(name = "upm_dept_leader")
interface UpmDeptLeaderEntity : UpmCreationEntity {
    @Column(name = "dept_id")
    val deptId: Long

    @Column(name = "user_id")
    val userId: Long

    @Column(name = "leader_type")
    @Default("primary")
    val leaderType: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "sort_order")
    @Default("0")
    val sortOrder: Int

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

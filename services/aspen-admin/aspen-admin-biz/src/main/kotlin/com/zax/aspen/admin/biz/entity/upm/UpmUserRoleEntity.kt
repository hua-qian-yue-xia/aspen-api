package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存用户角色授权范围、来源、周期和撤销轨迹 */
@Entity
@Table(name = "upm_user_role")
interface UpmUserRoleEntity : UpmCreationEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "role_id")
    val roleId: Long

    @Column(name = "scope_dept_id")
    val scopeDeptId: Long?

    @Column(name = "grant_source")
    @Default("manual")
    val grantSource: String

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "granted_by")
    val grantedBy: Long?

    @Column(name = "grant_reason")
    val grantReason: String?

    @Column(name = "revoked_at")
    val revokedAt: Instant?

    @Column(name = "revoked_by")
    val revokedBy: Long?

    @Column(name = "revoke_reason")
    val revokeReason: String?
}

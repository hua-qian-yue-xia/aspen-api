package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存用户临时、例外或显式拒绝的直接权限 */
@Entity
@Table(name = "upm_user_permission")
interface UpmUserPermissionEntity : UpmCreationEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "permission_id")
    val permissionId: Long

    @Column(name = "effect")
    @Default("allow")
    val effect: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "reason")
    val reason: String?

    @Column(name = "granted_by")
    val grantedBy: Long?
}

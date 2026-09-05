package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存角色到后端权限的授权关系 */
@Entity
@Table(name = "upm_role_permission")
interface UpmRolePermissionEntity : UpmCreationEntity {
    @Column(name = "role_id")
    val roleId: Long

    @Column(name = "permission_id")
    val permissionId: Long

    @Column(name = "grant_source")
    @Default("manual")
    val grantSource: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?
}

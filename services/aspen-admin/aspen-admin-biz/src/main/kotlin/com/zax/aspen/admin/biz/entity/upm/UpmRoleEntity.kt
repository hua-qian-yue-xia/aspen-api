package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存角色定义、默认数据范围和权限缓存版本 */
@Entity
@Table(name = "upm_role")
interface UpmRoleEntity : UpmMutableEntity {
    @Column(name = "role_code")
    val roleCode: String

    @Column(name = "name")
    val name: String

    @Column(name = "role_type")
    @Default("business")
    val roleType: String

    @Column(name = "owner_dept_id")
    val ownerDeptId: Long?

    @Column(name = "data_scope")
    @Default("self")
    val dataScope: String

    @Column(name = "is_built_in")
    @Default("false")
    val isBuiltIn: Boolean

    @Column(name = "is_assignable")
    @Default("true")
    val isAssignable: Boolean

    @Column(name = "priority")
    @Default("0")
    val priority: Int

    @Column(name = "permission_version")
    @Default("1")
    val permissionVersion: Int

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "description")
    val description: String?
}

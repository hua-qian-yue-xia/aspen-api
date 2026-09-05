package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存资源动作形式的后端权限定义 */
@Entity
@Table(name = "upm_permission")
interface UpmPermissionEntity : UpmMutableEntity {
    @Column(name = "permission_code")
    val permissionCode: String

    @Column(name = "name")
    val name: String

    @Column(name = "permission_type")
    @Default("api")
    val permissionType: String

    @Column(name = "resource")
    val resource: String

    @Column(name = "action")
    val action: String

    @Column(name = "risk_level")
    @Default("normal")
    val riskLevel: String

    @Column(name = "requires_audit")
    @Default("false")
    val requiresAudit: Boolean

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "description")
    val description: String?
}

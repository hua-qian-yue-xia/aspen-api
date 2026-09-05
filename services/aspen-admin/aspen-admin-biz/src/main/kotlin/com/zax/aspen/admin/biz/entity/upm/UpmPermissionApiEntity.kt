package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存权限到 HTTP API 路由的映射 */
@Entity
@Table(name = "upm_permission_api")
interface UpmPermissionApiEntity : UpmCreationEntity {
    @Column(name = "permission_id")
    val permissionId: Long

    @Column(name = "application")
    @Default("admin-api")
    val application: String

    @Column(name = "http_method")
    val httpMethod: String

    @Column(name = "path_pattern")
    val pathPattern: String

    @Column(name = "api_version")
    val apiVersion: String?

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存租户、默认区域、有效期和租户级配置 */
@Entity
@Table(name = "upm_tenant")
interface UpmTenantEntity : UpmRootMutableEntity {
    @Column(name = "tenant_code")
    val tenantCode: String

    @Column(name = "name")
    val name: String

    @Column(name = "short_name")
    val shortName: String?

    @Column(name = "domain")
    val domain: String?

    @Column(name = "logo_url")
    val logoUrl: String?

    @Column(name = "locale")
    @Default("zh-CN")
    val locale: String

    @Column(name = "timezone")
    @Default("Asia/Shanghai")
    val timezone: String

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?

    @Column(name = "max_users")
    val maxUsers: Int?

    @Serialized
    @Column(name = "configuration")
    val configuration: Map<String, Any?>?
}

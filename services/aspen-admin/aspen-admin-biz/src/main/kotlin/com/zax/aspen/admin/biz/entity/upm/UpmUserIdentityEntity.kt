package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存 OIDC、SAML、LDAP 等外部身份绑定 */
@Entity
@Table(name = "upm_user_identity")
interface UpmUserIdentityEntity : UpmMutableEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "provider")
    val provider: String

    @Column(name = "provider_tenant")
    @Default("default")
    val providerTenant: String

    @Column(name = "subject")
    val subject: String

    @Column(name = "external_username")
    val externalUsername: String?

    @Serialized
    @Column(name = "profile")
    val profile: Map<String, Any?>?

    @Default("now")
    @Column(name = "bound_at")
    val boundAt: Instant

    @Column(name = "last_login_at")
    val lastLoginAt: Instant?

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存用户 TOTP、WebAuthn 等多因素认证方式 */
@Entity
@Table(name = "upm_user_mfa")
interface UpmUserMfaEntity : UpmMutableEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "mfa_type")
    val mfaType: String

    /** 只允许保存应用层加密后的 MFA 密钥 */
    @Column(name = "encrypted_secret")
    val encryptedSecret: String?

    @Column(name = "credential_id")
    val credentialId: String?

    @Column(name = "is_primary")
    @Default("false")
    val isPrimary: Boolean

    @Column(name = "verified_at")
    val verifiedAt: Instant?

    @Column(name = "last_used_at")
    val lastUsedAt: Instant?

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

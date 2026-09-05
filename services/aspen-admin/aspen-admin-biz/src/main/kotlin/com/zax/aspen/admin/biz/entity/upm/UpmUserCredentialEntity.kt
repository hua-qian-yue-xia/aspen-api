package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存密码等可轮换凭证的摘要和锁定状态 */
@Entity
@Table(name = "upm_user_credential")
interface UpmUserCredentialEntity : UpmMutableEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "credential_type")
    @Default("password")
    val credentialType: String

    /** 只允许保存凭证摘要, 禁止写入明文 */
    @Column(name = "secret_hash")
    val secretHash: String

    @Column(name = "hash_algorithm")
    val hashAlgorithm: String

    @Column(name = "hash_version")
    val hashVersion: String?

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "expires_at")
    val expiresAt: Instant?

    @Column(name = "must_rotate")
    @Default("false")
    val mustRotate: Boolean

    @Column(name = "failed_attempt_count")
    @Default("0")
    val failedAttemptCount: Int

    @Column(name = "locked_until")
    val lockedUntil: Instant?

    @Column(name = "last_used_at")
    val lastUsedAt: Instant?

    @Column(name = "rotated_at")
    val rotatedAt: Instant?
}

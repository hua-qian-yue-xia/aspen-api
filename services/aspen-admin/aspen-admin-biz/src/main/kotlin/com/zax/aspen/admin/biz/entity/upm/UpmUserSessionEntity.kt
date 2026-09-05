package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存刷新令牌摘要、设备信息和会话生命周期 */
@Entity
@Table(name = "upm_user_session")
interface UpmUserSessionEntity : UpmMutableEntity {
    @Column(name = "user_id")
    val userId: Long

    @Column(name = "session_id")
    val sessionId: String

    /** 只允许保存刷新令牌摘要, 禁止写入原始令牌 */
    @Column(name = "refresh_token_hash")
    val refreshTokenHash: String

    @Column(name = "device_id")
    val deviceId: String?

    @Column(name = "client_name")
    val clientName: String?

    @Column(name = "client_version")
    val clientVersion: String?

    @Column(name = "ip_address")
    val ipAddress: String?

    @Column(name = "user_agent")
    val userAgent: String?

    @Column(name = "last_active_at")
    val lastActiveAt: Instant?

    @Column(name = "expires_at")
    val expiresAt: Instant

    @Column(name = "revoked_at")
    val revokedAt: Instant?

    @Column(name = "revoked_by")
    val revokedBy: Long?

    @Column(name = "revoke_reason")
    val revokeReason: String?

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

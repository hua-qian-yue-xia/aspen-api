package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存不可变的登录成功、失败、阻断和挑战审计记录 */
@Entity
@Table(name = "upm_login_log")
interface UpmLoginLogEntity : UpmTenantScopedEntity {
    @Column(name = "user_id")
    val userId: Long?

    @Column(name = "username_snapshot")
    val usernameSnapshot: String?

    @Column(name = "identity_provider")
    val identityProvider: String?

    @Column(name = "result")
    val result: String

    @Column(name = "failure_code")
    val failureCode: String?

    @Column(name = "failure_message")
    val failureMessage: String?

    @Column(name = "ip_address")
    val ipAddress: String?

    @Column(name = "user_agent")
    val userAgent: String?

    @Column(name = "device_id")
    val deviceId: String?

    @Column(name = "request_id")
    val requestId: String?

    @Column(name = "session_id")
    val sessionId: String?

    @Default("now")
    @Column(name = "occurred_at")
    val occurredAt: Instant

    @Serialized
    @Column(name = "context")
    val context: Map<String, Any?>?
}

package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存不可变的授权主体变更前后快照 */
@Entity
@Table(name = "upm_authorization_change_log")
interface UpmAuthorizationChangeLogEntity : UpmTenantScopedEntity {
    @Column(name = "operator_user_id")
    val operatorUserId: Long?

    @Column(name = "subject_type")
    val subjectType: String

    @Column(name = "subject_id")
    val subjectId: Long

    @Column(name = "action")
    val action: String

    @Serialized
    @Column(name = "before_snapshot")
    val beforeSnapshot: Map<String, Any?>?

    @Serialized
    @Column(name = "after_snapshot")
    val afterSnapshot: Map<String, Any?>?

    @Column(name = "reason")
    val reason: String?

    @Column(name = "request_id")
    val requestId: String?

    @Column(name = "ip_address")
    val ipAddress: String?

    @Default("now")
    @Column(name = "occurred_at")
    val occurredAt: Instant
}

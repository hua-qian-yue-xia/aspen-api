package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.MappedSuperclass
import java.time.Instant

/** 为服务自有的 Jimmer 实体提供可选审计时间字段 */
@MappedSuperclass
interface AuditableEntity {
    @Column(name = "created_at")
    val createdAt: Instant

    @Column(name = "updated_at")
    val updatedAt: Instant
}

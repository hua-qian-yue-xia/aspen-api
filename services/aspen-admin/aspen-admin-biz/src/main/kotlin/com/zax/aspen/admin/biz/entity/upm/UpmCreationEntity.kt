package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.MappedSuperclass
import java.time.Instant

/** 为不可变关系记录统一创建审计字段 */
@MappedSuperclass
interface UpmCreationEntity : UpmTenantScopedEntity {
    @Default("now")
    @Column(name = "created_at")
    val createdAt: Instant

    @Column(name = "created_by")
    val createdBy: Long?
}

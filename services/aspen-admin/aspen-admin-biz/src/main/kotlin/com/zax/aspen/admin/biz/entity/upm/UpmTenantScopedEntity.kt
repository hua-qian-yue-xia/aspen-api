package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.MappedSuperclass

/** 为租户隔离表统一租户标识映射 */
@MappedSuperclass
interface UpmTenantScopedEntity : UpmEntity {
    @Column(name = "tenant_id")
    val tenantId: Long
}

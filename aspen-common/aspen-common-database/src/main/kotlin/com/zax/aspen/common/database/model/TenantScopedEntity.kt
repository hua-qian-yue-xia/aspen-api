package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.MappedSuperclass

/** 为租户隔离表统一租户标识映射, 主键由各实体自行声明 */
@MappedSuperclass
interface TenantScopedEntity {
    val tenantId: Long
}

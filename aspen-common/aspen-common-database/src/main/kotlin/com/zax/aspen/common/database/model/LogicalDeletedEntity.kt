package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.LogicalDeleted
import org.babyfish.jimmer.sql.MappedSuperclass

/** 为需要保留历史数据的实体提供可选逻辑删除字段 */
@MappedSuperclass
interface LogicalDeletedEntity {
    /** 布尔逻辑删除标记, true 表示已删除; 需要删除时间与删除人审计时改用 DeleteAuditEntity */
    @LogicalDeleted("true")
    val deleted: Boolean
}

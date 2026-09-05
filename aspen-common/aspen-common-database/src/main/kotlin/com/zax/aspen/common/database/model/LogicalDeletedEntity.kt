package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.LogicalDeleted
import org.babyfish.jimmer.sql.MappedSuperclass

/** 为需要保留历史数据的实体提供可选逻辑删除字段 */
@MappedSuperclass
interface LogicalDeletedEntity {
    @LogicalDeleted("true")
    @Column(name = "deleted")
    val deleted: Boolean
}

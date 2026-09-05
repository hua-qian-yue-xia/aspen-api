package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.Version

/** 为需要并发更新保护的实体提供可选乐观锁字段, 版本从一开始 */
@MappedSuperclass
interface VersionedEntity {
    @Version
    @Default("1")
    val version: Int
}

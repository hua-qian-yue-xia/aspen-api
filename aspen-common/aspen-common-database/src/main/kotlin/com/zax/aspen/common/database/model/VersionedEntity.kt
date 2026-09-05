package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.Version

/** 为需要并发更新保护的实体提供可选乐观锁字段 */
@MappedSuperclass
interface VersionedEntity {
    @Version
    @Column(name = "version")
    val version: Int
}

package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.MappedSuperclass

/** 组合完整审计、时间戳逻辑删除与乐观锁的全量治理映射 */
@MappedSuperclass
interface MutableAuditEntity : AuditableEntity, DeleteAuditEntity, VersionedEntity

package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.MappedSuperclass

/** 组合创建与更新审计, 覆盖业务表的时间与操作人追溯 */
@MappedSuperclass
interface AuditableEntity : CreateAuditEntity, UpdateAuditEntity

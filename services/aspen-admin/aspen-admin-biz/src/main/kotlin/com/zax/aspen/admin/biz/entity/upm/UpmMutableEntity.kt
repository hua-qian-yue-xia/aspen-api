package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.MappedSuperclass

/** 为租户从属业务表组合可更新审计能力 */
@MappedSuperclass
interface UpmMutableEntity : UpmTenantScopedEntity, UpmMutableAuditEntity

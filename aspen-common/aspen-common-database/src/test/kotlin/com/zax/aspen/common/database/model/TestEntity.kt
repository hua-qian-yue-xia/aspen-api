package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id

/** 验证三种可选公共映射能力可以通过 KSP 组合继承的测试实体 */
@Entity
interface TestEntity : AuditableEntity, VersionedEntity, LogicalDeletedEntity {
    /** 使用数据库自增策略验证业务实体可以自行定义 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    /** 提供 KSP 编译测试所需的普通业务字段 */
    val name: String
}

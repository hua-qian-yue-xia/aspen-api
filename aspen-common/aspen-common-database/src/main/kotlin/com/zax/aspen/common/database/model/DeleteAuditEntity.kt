package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.LogicalDeleted
import org.babyfish.jimmer.sql.MappedSuperclass
import java.time.LocalDateTime

/** 为业务实体提供时间戳逻辑删除与删除人审计映射 */
@MappedSuperclass
interface DeleteAuditEntity {
    /** 使用删除时间作为逻辑删除标记, 保证误删数据可追溯和恢复 */
    @LogicalDeleted("now")
    val deletedAt: LocalDateTime?

    /** 删除主体标识, 各服务自定主体格式, 用户 ID 或服务身份统一转字符串保存 */
    val deletedBy: String?
}

package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.MappedSuperclass
import java.time.LocalDateTime

/** 为业务实体提供谁在什么时候创建的审计映射 */
@MappedSuperclass
interface CreateAuditEntity {
    @Default("now")
    val createdAt: LocalDateTime

    /** 创建主体标识, 各服务自定主体格式, 用户 ID 或服务身份统一转字符串保存 */
    val createdBy: String?
}

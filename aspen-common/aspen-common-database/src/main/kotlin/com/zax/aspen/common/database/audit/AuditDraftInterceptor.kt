package com.zax.aspen.common.database.audit

import com.zax.aspen.common.database.model.AuditableEntity
import com.zax.aspen.common.database.model.AuditableEntityDraft
import org.babyfish.jimmer.sql.DraftInterceptor
import java.time.Clock

/** 在不读取安全或租户上下文的情况下写入 UTC 审计时间 */
class AuditDraftInterceptor(
    /** 提供可替换且便于测试的当前时间 */
    private val clock: Clock,
) : DraftInterceptor<AuditableEntity, AuditableEntityDraft> {
    /** 新增时写入创建和更新时间, 更新时只写入更新时间 */
    override fun beforeSave(draft: AuditableEntityDraft, original: AuditableEntity?) {
        val now = clock.instant()
        if (original == null) {
            draft.createdAt = now
        }
        draft.updatedAt = now
    }
}

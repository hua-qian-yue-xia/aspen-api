package com.zax.aspen.common.database.audit

import com.zax.aspen.common.database.model.AuditableEntity
import com.zax.aspen.common.database.model.AuditableEntityDraft
import org.babyfish.jimmer.sql.DraftInterceptor
import java.time.Clock
import java.time.LocalDateTime

/** 在不读取安全或租户上下文的情况下写入部署域统一时区的审计时间 */
class AuditDraftInterceptor(
    /** 提供可替换且便于测试的当前时间 */
    private val clock: Clock,
) : DraftInterceptor<AuditableEntity, AuditableEntityDraft> {
    /**
     * 新增时同时写入 createdAt 与 updatedAt, 更新时只重写 updatedAt
     *
     * 时间统一取自构造注入的 Clock, 与接口通用的保存前回调相比限定了按场景写时间的差异行为
     *
     * @param draft 待保存实体的草稿对象, 审计时间直接写入该草稿
     * @param original 保存前的原始实体, 新增时为 `null`
     */
    override fun beforeSave(draft: AuditableEntityDraft, original: AuditableEntity?) {
        val now = LocalDateTime.now(clock)
        if (original == null) {
            draft.createdAt = now
        }
        draft.updatedAt = now
    }
}

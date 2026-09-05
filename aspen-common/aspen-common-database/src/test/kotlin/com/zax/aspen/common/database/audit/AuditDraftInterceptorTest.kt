package com.zax.aspen.common.database.audit

import com.zax.aspen.common.database.model.AuditableEntity
import com.zax.aspen.common.database.model.AuditableEntityDraft
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 验证审计拦截器对新增和更新操作使用不同写入规则 */
class AuditDraftInterceptorTest {
    /** 为审计断言提供固定时间 */
    private val now: LocalDateTime = LocalDateTime.parse("2026-09-02T06:30:00")

    /** 使用固定时钟创建待测审计拦截器 */
    private val interceptor = AuditDraftInterceptor(Clock.fixed(Instant.parse("2026-09-02T06:30:00Z"), ZoneOffset.UTC))

    /** 验证新增实体会同时写入创建时间和更新时间 */
    @Test
    fun `sets both timestamps for a new entity`() {
        val draft = mock(AuditableEntityDraft::class.java)

        interceptor.beforeSave(draft, null)

        verify(draft).createdAt = now
        verify(draft).updatedAt = now
    }

    /** 验证更新实体只写入更新时间并保留原创建时间 */
    @Test
    fun `preserves creation timestamp for an existing entity`() {
        val draft = mock(AuditableEntityDraft::class.java)
        val original = mock(AuditableEntity::class.java)

        interceptor.beforeSave(draft, original)

        verify(draft, never()).createdAt = now
        verify(draft).updatedAt = now
    }
}

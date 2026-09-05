package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedEntity
import com.zax.aspen.common.database.model.TenantScopedEntityDraft
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertFailsWith

/** 验证租户拦截器对新增和更新操作使用不同写入规则 */
class TenantDraftInterceptorTest {
    /** 验证新增实体从服务端上下文填充租户标识 */
    @Test
    fun `fills tenant from context for a new entity`() {
        val draft = mock(TenantScopedEntityDraft::class.java)
        val interceptor = TenantDraftInterceptor { 42L }

        interceptor.beforeSave(draft, null)

        verify(draft).tenantId = 42L
    }

    /** 验证缺失租户上下文时拒绝保存新增数据 */
    @Test
    fun `rejects saving without tenant context`() {
        val draft = mock(TenantScopedEntityDraft::class.java)
        val interceptor = TenantDraftInterceptor { null }

        assertFailsWith<IllegalStateException> { interceptor.beforeSave(draft, null) }
    }

    /** 验证更新既有数据不重写租户列 */
    @Test
    fun `does not rewrite tenant for an existing entity`() {
        val draft = mock(TenantScopedEntityDraft::class.java)
        val original = mock(TenantScopedEntity::class.java)
        val interceptor = TenantDraftInterceptor { 42L }

        interceptor.beforeSave(draft, original)

        verify(draft, never()).tenantId = 42L
    }
}

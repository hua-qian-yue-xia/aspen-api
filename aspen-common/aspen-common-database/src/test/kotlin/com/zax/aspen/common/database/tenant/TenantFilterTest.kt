package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedProps
import org.babyfish.jimmer.sql.ast.Predicate
import org.babyfish.jimmer.sql.ast.PropExpression
import org.babyfish.jimmer.sql.filter.FilterArgs
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 验证租户过滤器对查询条件和缺失上下文行为的处理 */
class TenantFilterTest {
    /** 验证存在租户上下文时把租户等值条件追加到查询 */
    @Test
    fun `appends tenant predicate when context exists`() {
        val predicate = mock(Predicate::class.java)
        val tenantIdProp = mockPropExpression(predicate)
        val args = mockArgs(tenantIdProp)
        val filter = TenantFilter { 42L }

        filter.filter(args)

        verify(args).where(predicate)
    }

    /** 验证缺失租户上下文时拒绝查询, 不允许 fail-open */
    @Test
    fun `rejects queries without tenant context`() {
        val args = mockArgs()
        val filter = TenantFilter { null }

        assertFailsWith<IllegalStateException> { filter.filter(args) }
    }

    /** 构造携带 tenantId 属性表达式的过滤参数 */
    @Suppress("UNCHECKED_CAST")
    private fun mockPropExpression(predicate: Predicate): PropExpression<Long> {
        val propExpression = mock(PropExpression::class.java) as PropExpression<Long>
        Mockito.`when`(propExpression.eq(42L)).thenReturn(predicate)
        return propExpression
    }

    /** 构造携带租户属性列的过滤参数 mock */
    @Suppress("UNCHECKED_CAST")
    private fun mockArgs(tenantIdProp: PropExpression<Long>? = null): FilterArgs<TenantScopedProps> {
        val table = mock(TenantScopedProps::class.java)
        if (tenantIdProp != null) {
            Mockito.`when`(table.get<Long>("tenantId")).thenReturn(tenantIdProp)
        }
        val args = mock(FilterArgs::class.java) as FilterArgs<TenantScopedProps>
        Mockito.`when`(args.table).thenReturn(table)
        return args
    }
}

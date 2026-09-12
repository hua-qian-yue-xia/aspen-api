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

/**
 * 验证租户过滤器对查询条件和缺失上下文行为的处理
 */
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

    /** 验证显式系统上下文内不追加租户条件, 声明了审计动机的跨租户读放行 */
    @Test
    fun `skips tenant predicate inside explicit system context`() {
        val args = mockArgs()
        val filter = TenantFilter { null }

        TenantSystemContext.runAsSystem("tenant-filter-test") { filter.filter(args) }

        Mockito.verify(args, Mockito.never()).where(Mockito.any(Predicate::class.java))
    }

    /** 验证系统上下文结束后恢复 fail-closed, 缺失租户上下文再次拒绝查询 */
    @Test
    fun `restores fail closed after system context ends`() {
        val args = mockArgs()
        val filter = TenantFilter { null }

        TenantSystemContext.runAsSystem("tenant-filter-test") { filter.filter(args) }

        assertFailsWith<IllegalStateException> { filter.filter(args) }
    }

    /**
     * 构造 eq(42L) 命中时返回指定条件的租户属性表达式 mock
     *
     * @param predicate eq(42L) 时返回的目标查询条件
     * @return mock 自 PropExpression 并强转为 Long 类型的替身
     */
    @Suppress("UNCHECKED_CAST")
    private fun mockPropExpression(predicate: Predicate): PropExpression<Long> {
        val propExpression = mock(PropExpression::class.java) as PropExpression<Long>
        Mockito.`when`(propExpression.eq(42L)).thenReturn(predicate)
        return propExpression
    }

    /**
     * 构造携带租户属性列的过滤参数 mock
     *
     * @param tenantIdProp table 取 tenantId 属性时返回的表达式, 传 `null` 时不设置该桩
     * @return mock 自 FilterArgs 的替身, 其 table 返回 TenantScopedProps mock
     */
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

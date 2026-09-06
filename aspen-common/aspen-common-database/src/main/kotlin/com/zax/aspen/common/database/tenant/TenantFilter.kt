package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedProps
import org.babyfish.jimmer.sql.filter.Filter
import org.babyfish.jimmer.sql.filter.FilterArgs

/** 为全部租户隔离实体的查询自动追加当前租户条件 */
class TenantFilter(
    /** 提供当前请求的租户标识 */
    private val tenantContextSupplier: TenantContextSupplier,
) : Filter<TenantScopedProps> {
    /**
     * 为当前查询追加 tenantId 相等条件, 实现租户行级隔离
     *
     * 与接口通用的过滤器契约相比, 本实现在租户上下文缺失时选择 fail-closed,
     * 拒绝查询以防止跨租户数据泄漏
     *
     * @param args Jimmer 过滤器参数, 租户条件经 args.where 追加到当前查询
     * @throws IllegalStateException 租户上下文缺失时拒绝执行查询
     */
    override fun filter(args: FilterArgs<TenantScopedProps>) {
        val tenantId =
            tenantContextSupplier.get() ?: throw IllegalStateException("缺少租户上下文, 拒绝执行租户数据查询")
        val tenantIdProp = args.table.get<Long>("tenantId")
        args.where(tenantIdProp.eq(tenantId))
    }
}

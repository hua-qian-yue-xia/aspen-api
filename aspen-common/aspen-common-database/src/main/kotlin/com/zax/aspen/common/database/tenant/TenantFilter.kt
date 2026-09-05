package com.zax.aspen.common.database.tenant

import com.zax.aspen.common.database.model.TenantScopedProps
import org.babyfish.jimmer.sql.filter.Filter
import org.babyfish.jimmer.sql.filter.FilterArgs

/** 为全部租户隔离实体的查询自动追加当前租户条件 */
class TenantFilter(
    /** 提供当前请求的租户标识 */
    private val tenantContextSupplier: TenantContextSupplier,
) : Filter<TenantScopedProps> {
    /** 无租户上下文时拒绝查询, 防止 fail-open 造成跨租户数据泄漏 */
    override fun filter(args: FilterArgs<TenantScopedProps>) {
        val tenantId =
            tenantContextSupplier.get() ?: throw IllegalStateException("缺少租户上下文, 拒绝执行租户数据查询")
        val tenantIdProp = args.table.get<Long>("tenantId")
        args.where(tenantIdProp.eq(tenantId))
    }
}

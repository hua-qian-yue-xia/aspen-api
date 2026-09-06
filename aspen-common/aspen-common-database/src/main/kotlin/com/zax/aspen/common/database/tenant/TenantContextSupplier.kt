package com.zax.aspen.common.database.tenant

/** 提供当前请求的租户标识, 由各服务从自身安全或请求上下文装配 */
fun interface TenantContextSupplier {
    /**
     * 返回当前请求的租户标识, 供租户过滤器与保存拦截器消费
     *
     * @return 当前租户 ID, 无租户上下文时返回 `null`
     */
    fun get(): Long?
}

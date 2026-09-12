package com.zax.aspen.admin.biz.tenant

/**
 * 租户头请求级上下文
 *
 * 以 ThreadLocal 承载当前请求解析出的租户标识: 过滤器进入时写入、请求结束时
 * 清理, TenantContextSupplier 适配器在同线程读取; 异步线程不传播, 跨线程协作
 * 必须显式传递租户上下文
 */
object TenantHeaderContext {
    /** 当前线程的租户标识, 未携带时为 null */
    private val currentTenantId: ThreadLocal<Long?> = ThreadLocal.withInitial { null }

    /**
     * 写入当前线程的租户标识
     *
     * @param tenantId 解析成功的租户标识
     */
    fun set(tenantId: Long) {
        currentTenantId.set(tenantId)
    }

    /**
     * 读取当前线程的租户标识
     *
     * @return 租户标识, 未携带时返回 null (下游 fail-closed 拒绝租户数据操作)
     */
    fun get(): Long? = currentTenantId.get()

    /** 清理当前线程的租户标识, 请求结束必须调用防止线程池串号 */
    fun clear() {
        currentTenantId.remove()
    }
}

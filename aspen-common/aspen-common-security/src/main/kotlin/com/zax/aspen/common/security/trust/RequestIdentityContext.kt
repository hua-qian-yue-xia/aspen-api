package com.zax.aspen.common.security.trust

/**
 * 网关注入身份的请求级上下文
 *
 * 以 ThreadLocal 承载 InternalTrustFilter 校验通过后解析出的当前身份: 过滤器进入
 * 时写入、请求结束时清理, TenantContextSupplier 适配器与业务代码在同线程读取;
 * 异步线程不传播, 跨线程协作必须显式传递身份; 值只读, 网关是唯一合法写入方,
 * 业务进程不得回写
 */
object RequestIdentityContext {
    /** 当前线程的身份, 未经过信任链或无身份头时为 null */
    private val currentIdentity: ThreadLocal<Identity?> = ThreadLocal.withInitial { null }

    /**
     * 读取当前线程的身份
     *
     * @return 信任链校验后的身份, 匿名请求或尚未进入过滤器时返回 `null`
     */
    fun get(): Identity? = currentIdentity.get()

    /**
     * 写入当前线程的身份, 仅供 InternalTrustFilter 调用
     *
     * @param identity 校验通过后解析出的身份
     */
    internal fun set(identity: Identity) {
        currentIdentity.set(identity)
    }

    /** 清理当前线程的身份, 请求结束必须调用防止线程池串号 */
    fun clear() {
        currentIdentity.remove()
    }

    /**
     * 信任链校验后的只读身份
     *
     * @property userId 用户域内主体标识, 请求未关联主体 (匿名) 时为 null
     * @property clientKind 端类型 code (AuthClientKind), 网关按令牌 claim 注入
     * @property tenantId 租户标识, 主体无租户 (C 端) 或请求未携带时为 null
     */
    data class Identity(
        val userId: Long?,
        val clientKind: String?,
        val tenantId: Long?,
    )
}

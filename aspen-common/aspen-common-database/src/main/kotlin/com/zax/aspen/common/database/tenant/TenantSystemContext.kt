package com.zax.aspen.common.database.tenant

/**
 * 显式系统上下文: 跨租户操作的唯一合法逃生口
 *
 * 技术架构 14.2: 租户查询与保存 fail-closed, 跨租户操作必须使用显式系统上下文
 * 并保留审计。本对象以 ThreadLocal 承载一次显式声明的系统级执行段: 段内
 * TenantFilter 不追加租户条件 (登录按账号找主体、管理面跨租户巡检), 租户隔离
 * 实体的新增使用声明的 tenantId 而非请求上下文; 段外一切照旧 fail-closed。
 * reason 是必填的审计说明, 调用方须保证可追溯 (写日志或进入审计事件);
 * 异步线程不传播, 跨线程协作必须显式重开执行段
 */
object TenantSystemContext {
    /** 当前线程的系统上下文声明, 未进入执行段时为 null */
    private val currentOverride: ThreadLocal<SystemOverride?> = ThreadLocal.withInitial { null }

    /**
     * 读取当前线程的系统上下文声明
     *
     * @return 活跃的系统上下文声明, 不在执行段内时返回 `null`
     */
    fun current(): SystemOverride? = currentOverride.get()

    /**
     * 在显式系统上下文中执行代码块, 结束后自动恢复 fail-closed 语义
     *
     * @param reason 审计说明, 必须能定位到业务动机 (如 auth-principal-spi)
     * @param tenantId 段内新增租户隔离数据时使用的租户标识, 查询场景可省略
     * @param block 需要跨租户访问的代码块
     * @return 代码块的执行结果
     */
    fun <T> runAsSystem(reason: String, tenantId: Long? = null, block: () -> T): T {
        require(reason.isNotBlank()) { "系统上下文必须携带审计说明" }
        currentOverride.set(SystemOverride(reason, tenantId))
        try {
            return block()
        } finally {
            currentOverride.remove()
        }
    }

    /**
     * 一次显式声明的系统级执行上下文
     *
     * @property reason 审计说明, 定位跨租户操作的业务动机
     * @property tenantId 段内新增租户隔离数据使用的租户标识, 查询场景为 null
     */
    data class SystemOverride(
        val reason: String,
        val tenantId: Long?,
    )
}

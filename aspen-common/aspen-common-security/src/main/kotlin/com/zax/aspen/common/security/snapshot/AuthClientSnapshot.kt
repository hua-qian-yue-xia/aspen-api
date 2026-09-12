package com.zax.aspen.common.security.snapshot

/**
 * 认证客户端 (端) 的分发快照
 *
 * sys_auth_client 单行及其全部启用登录方式到快照的映射: disabled 的端与方式行
 * 在发布前已被 Admin 过滤, 不进入快照; 机器端密钥摘要不进入快照——CLIENT_SECRET
 * 方式的验密在 Auth 读库完成, 分发介质只承载认证引擎的策略判定输入
 */
data class AuthClientSnapshot(
    /** 端编码, 全局唯一, 创建后不可修改 */
    val clientCode: String,
    /** 端类型 code, 对应 AuthClientKind */
    val clientKind: String,
    /** 访问令牌 TTL 覆盖 (秒), null 表示用认证引擎的全局默认值 */
    val accessTokenTtlSeconds: Long?,
    /** 刷新令牌 TTL 覆盖 (秒), null 表示用认证引擎的全局默认值 */
    val refreshTokenTtlSeconds: Long?,
    /** 该端全部启用的登录方式, 展示顺序已按 sort_order 排列 */
    val methods: List<AuthLoginMethodSnapshot>,
) {
    init {
        require(clientCode.isNotBlank()) { "客户端快照的 clientCode 不能为空" }
        require(clientKind.isNotBlank()) { "客户端快照的 clientKind 不能为空" }
        val duplicatedMethods = methods.groupBy { it.method }.filterValues { it.size > 1 }.keys
        require(duplicatedMethods.isEmpty()) { "客户端 $clientCode 存在重复登录方式: $duplicatedMethods" }
    }
}

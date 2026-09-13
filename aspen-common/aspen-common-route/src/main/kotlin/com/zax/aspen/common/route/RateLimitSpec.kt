package com.zax.aspen.common.route

/**
 * 端点限流声明
 *
 * 由 common-web 的限流拦截器消费: 进程内固定窗口计数, 窗口自第一次命中起算, 超限抛
 * `COMMON.TOO_MANY_REQUESTS` 渲染 429 Problem Details; 语义是「每实例配额」, 多实例部署
 * 时总量约 N 倍, 需要全局限量时由公共层替换 Redis 实现而注解不变
 *
 * @property windowSeconds 窗口长度 (秒), 非正值视为不限流
 * @property limit 窗口内允许的最大请求数, 非正值视为不限流
 * @property scope 计数作用域, 主体隔离或全主体共享
 */
annotation class RateLimitSpec(
    val windowSeconds: Int = 60,
    val limit: Int = 100,
    val scope: RateLimitScope = RateLimitScope.USER,
)

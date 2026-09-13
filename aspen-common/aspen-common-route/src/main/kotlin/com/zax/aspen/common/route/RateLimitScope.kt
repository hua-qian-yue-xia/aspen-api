package com.zax.aspen.common.route

/**
 * 路由限流的作用域
 *
 * 限流计数按「作用域主体 + 映射 pattern + HTTP 方法」组合成 key, 同 key 共享一个窗口桶
 */
enum class RateLimitScope {
    /** 按认证主体限量: 有身份上下文时按主体隔离 (端类型 + userId), 无身份上下文回退来源 IP, 再回退匿名共享桶 */
    USER,

    /** 全主体共享单桶: 端点级别的整体配额保护, 任意调用方共同消耗 */
    GLOBAL,
}

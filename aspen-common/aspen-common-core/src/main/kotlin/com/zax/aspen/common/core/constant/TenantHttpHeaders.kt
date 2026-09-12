package com.zax.aspen.common.core.constant

/**
 * 租户标识跨服务传播的 HTTP 头常量
 *
 * 技术架构 14.2: 租户上下文由 Gateway/Auth 校验令牌后确定并随内部 Header 透传,
 * 业务进程经显式装配的 TenantContextSupplier 消费, 缺失即 fail-closed;
 * 发送方 (网关 / 统一任务服务的逐租户投递) 与接收方 (各业务服务的租户头装配)
 * 共同引用本常量, 避免头名漂移; 收取方装配必须显式开启并仅信任内网调用链,
 * 外部流量经网关时该头由网关覆盖重写, 不透传客户端伪造值
 */
object TenantHttpHeaders {
    /** 租户标识头, 值为 upm_tenant 的 tenant_id 十进制文本 */
    const val TENANT_ID = "X-Aspen-Tenant-Id"
}

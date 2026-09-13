package com.zax.aspen.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 网关内部身份注入配置
 *
 * 信任凭据是网关对业务进程证明「本请求的身份头由我重写」的唯一凭据, 值只经
 * 环境变量 ASPEN_GATEWAY_INTERNAL_SECRET 注入, 与业务侧 aspen.security.
 * trust-token 保持一致; 未配置时不注入身份头, 业务侧 fail-closed 拒绝
 */
@ConfigurationProperties(prefix = "aspen.gateway.internal")
class GatewayInternalProperties(
    /** 注入 X-Aspen-Gateway-Trust 头使用的信任凭据, 未配置时不注入身份头 */
    var trustToken: String? = null,
)

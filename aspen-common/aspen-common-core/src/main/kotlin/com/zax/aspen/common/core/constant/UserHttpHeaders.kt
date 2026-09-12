package com.zax.aspen.common.core.constant

/**
 * 用户身份与网关信任跨服务传播的 HTTP 头常量
 *
 * 技术架构 14.2: Gateway 校验外部令牌后按令牌重新注入用户身份头 (剥离客户端伪造值),
 * 并携带网关信任凭据随内部调用链透传; 业务进程经 aspen-common-security 的最小信任链
 * 校验信任头后消费身份头, 信任缺失而身份头存在即判定伪造并拒绝; 发送方 (网关身份头
 * 注入过滤器) 与接收方 (common-security 的 InternalTrustFilter) 共同引用本常量,
 * 避免头名漂移; 租户头单独维护在 [TenantHttpHeaders], 与用户身份头正交
 */
object UserHttpHeaders {
    /** 用户主体标识头, 值为用户域内 principal_id 十进制文本, 跨用户域唯一性由端类型头限定 */
    const val USER_ID = "X-Aspen-User-Id"

    /** 端类型头, 值为 AuthClientKind 的 code (admin/app/device/third_party), 网关据此做 claim↔前缀校验 */
    const val CLIENT_KIND = "X-Aspen-Client-Kind"

    /** 网关信任凭据头, 值来自部署环境变量 ASPEN_GATEWAY_INTERNAL_SECRET, 网关注入、业务侧比对 */
    const val GATEWAY_TRUST_TOKEN = "X-Aspen-Gateway-Trust"
}

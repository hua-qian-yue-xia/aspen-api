package com.zax.aspen.auth.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * 登出请求
 *
 * 登出吊销 auth_session (revoked_at), 刷新链路随之断绝; 访问令牌不做黑名单,
 * 靠短 TTL 自然过期, 这是明确的产品语义; 请求经网关携带 Bearer 访问令牌,
 * 刷新令牌用于定位并吊销会话
 */
data class LogoutRequest(
    /** 待吊销会话对应的刷新令牌 */
    @field:NotBlank
    val refreshToken: String,
)

package com.zax.aspen.auth.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * 刷新令牌请求
 *
 * 刷新为轮换语义: 认证引擎比对 auth_session 中的摘要、复查主体状态后作废旧刷新
 * 令牌并签发新的令牌对; 旧令牌二次使用命中已轮换会话, 按策略吊销整条会话
 * (防盗用), 返回 401
 */
data class RefreshRequest(
    /** 登录或上次刷新下发的刷新令牌 */
    @field:NotBlank
    val refreshToken: String,
)

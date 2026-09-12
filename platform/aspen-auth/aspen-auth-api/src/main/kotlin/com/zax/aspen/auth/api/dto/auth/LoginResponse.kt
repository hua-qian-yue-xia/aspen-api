package com.zax.aspen.auth.api.dto.auth

/**
 * 登录与刷新的令牌回执
 *
 * 访问令牌为 JWT (RS256, 短 TTL), 刷新令牌为不透明随机值 (轮换式); 登录触发
 * 强制改密策略时仍签发令牌并置位 mustChangePassword, 前端引导改密; 「改密完成前
 * 阻断其他 API」的硬约束属路由级 RBAC 能力, 随后续批次收紧, 本契约只携带状态
 */
data class LoginResponse(
    /** 访问令牌 (JWT), 后续请求以 Bearer 方式携带经网关验签 */
    val accessToken: String,
    /** 刷新令牌 (不透明随机值), 单次有效, 刷新即轮换, 摘要存 auth_session */
    val refreshToken: String,
    /** 访问令牌有效期 (秒), 由端的 access_token_ttl_seconds 或全局默认决定 */
    val expiresIn: Long,
    /** 是否触发强制改密 (首登策略或密码过期), 前端据此引导改密流程 */
    val mustChangePassword: Boolean,
    /** 主体展示名, 登录成功回执的可读信息 */
    val displayName: String,
)

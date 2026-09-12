package com.zax.aspen.auth.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * 账号密码登录请求
 *
 * 由端前缀隐含登录发生的端 (管理端经 /admin-api/auth/login, app 端经
 * /app-api/auth/login), 请求体不携带端标识, 认证引擎按路径前缀对应的端查询
 * 登录方式策略; captchaToken 为三方行为验证码前端票据, 方式策略为 NONE 时可不传
 */
data class LoginRequest(
    /** 登录账号, 用户域内可定位主体的任意账号形态 (用户名/手机号/邮箱) */
    @field:NotBlank
    val account: String,
    /** 密码明文, 仅经 HTTPS 内网链路传输, Auth 与用户域都不落日志 */
    @field:NotBlank
    val password: String,
    /** 验证码票据, 方式策略闸门为 SLIDER/IMAGE 时必传, NONE 时忽略 */
    val captchaToken: String?,
)

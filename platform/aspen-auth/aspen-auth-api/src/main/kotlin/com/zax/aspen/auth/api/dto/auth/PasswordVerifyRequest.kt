package com.zax.aspen.auth.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * 密码校验请求
 *
 * Principal SPI verifyPassword 的入参: Auth 把登录提交的账号与密码明文交给用户域,
 * 摘要比对、失败计数与锁定判定全部在用户域完成, Auth 不接触摘要与锁定状态机
 */
data class PasswordVerifyRequest(
    /** 登录账号, 用户域内可定位主体的任意账号形态 (用户名/手机号/邮箱), 由用户域解释 */
    @field:NotBlank
    val account: String,
    /** 密码明文, 仅在内网调用链 (Auth -> 用户域 internal 端点) 传输, 双方都不落日志 */
    @field:NotBlank
    val secret: String,
)

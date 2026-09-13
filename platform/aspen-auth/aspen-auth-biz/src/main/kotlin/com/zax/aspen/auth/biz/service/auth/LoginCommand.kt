package com.zax.aspen.auth.biz.service.auth

import com.zax.aspen.auth.api.dto.auth.LoginRequest

/**
 * 登录指令: 请求体 + 端与来源上下文
 *
 * 端由控制器按路径前缀 (受众包) 钉死, 请求体不携带端标识; 来源信息供会话
 * 与登录审计留痕
 *
 * @property clientKind 端类型, 由控制器从路径前缀推导
 * @property request 登录请求体 (账号、密码、验证码票据)
 * @property ip 登录来源 IP
 * @property userAgent 登录 User-Agent
 */
data class LoginCommand(
    val clientKind: com.zax.aspen.auth.api.enums.auth.AuthClientKind,
    val request: LoginRequest,
    val ip: String?,
    val userAgent: String?,
)

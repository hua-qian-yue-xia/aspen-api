package com.zax.aspen.auth.biz.controller.admin.auth

import com.zax.aspen.auth.api.contract.auth.AuthLoginApi
import com.zax.aspen.auth.api.dto.auth.LoginRequest
import com.zax.aspen.auth.api.dto.auth.LoginResponse
import com.zax.aspen.auth.api.dto.auth.LogoutRequest
import com.zax.aspen.auth.api.dto.auth.RefreshRequest
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.biz.service.auth.AuthLoginService
import com.zax.aspen.auth.biz.service.auth.LoginCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 管理端登录端点 (AuthLoginApi 的管理端实现)
 *
 * 受众包 controller/admin 经 common-web 注册期挂 /admin-api 前缀, 端类型由
 * 路径钉死为 ADMIN; login 与 refresh 属网关公开路径白名单, logout 携带
 * Bearer 访问令牌经网关验签后转发; 来源信息经 RequestContextHolder 读取,
 * 非 Web 上下文调用时来源为 null, 不阻断登录
 */
@RestController
class AdminAuthLoginController(
    private val authLoginService: AuthLoginService,
) : AuthLoginApi {
    /**
     * 账号密码登录
     *
     * @param request 账号、密码与验证码票据
     * @return 令牌对与主体回执
     */
    override fun login(@RequestBody @Valid request: LoginRequest): LoginResponse =
        authLoginService.login(request.toCommand())

    /**
     * 刷新访问令牌 (轮换式)
     *
     * @param request 待轮换的刷新令牌
     * @return 新的令牌对
     */
    override fun refresh(@RequestBody @Valid request: RefreshRequest): LoginResponse =
        authLoginService.refresh(request.refreshToken)

    /**
     * 登出并吊销会话, 幂等
     *
     * @param request 待吊销会话的刷新令牌
     */
    override fun logout(@RequestBody @Valid request: LogoutRequest) {
        authLoginService.logout(request.refreshToken)
    }

    /**
     * 把请求体补齐端与来源上下文构造成登录指令
     *
     * @receiver 登录请求体
     * @return 携带管理端类型与来源信息的登录指令
     */
    private fun LoginRequest.toCommand(): LoginCommand {
        val currentRequest = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        return LoginCommand(
            clientKind = AuthClientKind.ADMIN,
            request = this,
            ip = currentRequest?.remoteAddr,
            userAgent = currentRequest?.getHeader("User-Agent"),
        )
    }
}

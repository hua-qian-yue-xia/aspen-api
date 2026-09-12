package com.zax.aspen.auth.api.contract.auth

import com.zax.aspen.auth.api.dto.auth.LoginRequest
import com.zax.aspen.auth.api.dto.auth.LoginResponse
import com.zax.aspen.auth.api.dto.auth.LogoutRequest
import com.zax.aspen.auth.api.dto.auth.RefreshRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 对外登录端点契约
 *
 * 由 aspen-auth-biz 实现: 控制器落在 controller/admin 包 (管理端, 网关前缀
 * /admin-api/auth) 与 controller/app 包 (app 端, /app-api/auth), common-web
 * 注册期自动挂前缀; login 与 refresh 属网关公开路径白名单, logout 携带 Bearer
 * 访问令牌经网关验签; 端由路径前缀隐含, 请求体不携带端标识
 */
interface AuthLoginApi {
    /**
     * 账号密码登录
     *
     * @param request 账号、密码与验证码票据
     * @return 令牌对与主体回执; 触发强制改密策略时仍签发令牌并置位标记
     */
    @PostMapping("$PATH/login")
    fun login(@RequestBody @Valid request: LoginRequest): LoginResponse

    /**
     * 刷新访问令牌 (轮换式)
     *
     * @param request 待轮换的刷新令牌
     * @return 新的令牌对, 旧刷新令牌随即作废
     */
    @PostMapping("$PATH/refresh")
    fun refresh(@RequestBody @Valid request: RefreshRequest): LoginResponse

    /**
     * 登出并吊销会话
     *
     * @param request 待吊销会话的刷新令牌
     */
    @PostMapping("$PATH/logout")
    fun logout(@RequestBody @Valid request: LogoutRequest)

    companion object {
        /** 登录端点相对路径前缀, 经 common-web 挂端前缀后为 /admin-api/auth、/app-api/auth */
        const val PATH = "/auth"
    }
}

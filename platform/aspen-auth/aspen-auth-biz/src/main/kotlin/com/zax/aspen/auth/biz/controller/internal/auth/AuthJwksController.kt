package com.zax.aspen.auth.biz.controller.internal.auth

import com.zax.aspen.auth.biz.token.AuthTokenService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * JWKS 发布端点
 *
 * 网关的 JWT 验签公钥来源: 只发布公钥参数 (n/e/kid/use/alg), 私钥永不离开
 * Auth 进程; 不经网关暴露, 内网直连读取; kid 轮换期可扩展为多键列表
 */
@RestController
class AuthJwksController(
    private val authTokenService: AuthTokenService,
) {
    /**
     * 输出 JWKS 文档
     *
     * @return 标准 JWKS 结构 {"keys": [...]}, 当前含当前签发公钥单键
     */
    @GetMapping(PATH)
    fun jwks(): Map<String, Any> = mapOf("keys" to listOf(authTokenService.publicJwk().toJSONObject()))

    private companion object {
        /** JWKS 端点路径 (internal, 不经网关) */
        const val PATH = "/internal/auth/jwks"
    }
}

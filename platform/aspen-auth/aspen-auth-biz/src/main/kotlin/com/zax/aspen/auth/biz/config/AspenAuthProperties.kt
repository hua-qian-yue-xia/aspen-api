package com.zax.aspen.auth.biz.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 统一认证服务运行配置
 *
 * 密钥类取值 (JWT 私钥、三方验证码凭据) 只经环境变量注入后落位本配置, 不写
 * 配置中心与仓库 (技术架构 14.2 密钥边界); 端点地址类取值允许本地开发默认值
 */
@ConfigurationProperties(prefix = "aspen.auth")
class AspenAuthProperties(
    /** JWT 签发配置 */
    val jwt: Jwt = Jwt(),
    /** 主体 SPI 的用户域端点配置 */
    val principal: Principal = Principal(),
    /** 三方行为验证码闸门配置 */
    val captcha: Captcha = Captcha(),
) {
    /** JWT 签发参数 */
    class Jwt(
        /** RS256 私钥 (PKCS#8 base64); 为空表示未配置, 签发入口快速失败 */
        var privateKey: String = "",
        /** 密钥标识 (kid), JWKS 与令牌头对齐, 轮换期可同时发布多把公钥 */
        var keyId: String = "aspen-default",
        /** 访问令牌全局默认 TTL (秒), 端未覆盖时使用 */
        var accessTokenTtlSeconds: Long = 1800,
        /** 刷新令牌全局默认 TTL (秒), 端未覆盖时使用 */
        var refreshTokenTtlSeconds: Long = 604800,
        /** 令牌签发方标识, 写入 iss claim */
        var issuer: String = "aspen-auth",
    )

    /** 主体 SPI 端点参数 */
    class Principal(
        /** 用户域 (Admin) 的基础地址, 本地开发默认直连 7100 */
        var baseUrl: String = "http://localhost:7100",
    )

    /** 三方行为验证码闸门参数 */
    class Captcha(
        /** 服务商服务端二次校验端点; 为空表示 SLIDER 闸门未接线, 命中即拒绝 */
        var validateUrl: String = "",
        /** 服务商凭据 ID, 只经环境变量注入 */
        var credentialId: String = "",
        /** 服务商凭据密钥, 只经环境变量注入 */
        var credentialSecret: String = "",
    )
}

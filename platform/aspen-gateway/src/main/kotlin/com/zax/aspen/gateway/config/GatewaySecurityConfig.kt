package com.zax.aspen.gateway.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * 网关安全链: 外部令牌校验的唯一落点
 *
 * 技术架构 14.2: 公开路径 (登录/刷新/健康检查) 白名单放行, 其余交换一律要求
 * JWT 认证 (RS256 验签密钥经 Auth 的 JWKS 分发, spring.security.oauth2.
 * resourceserver.jwt.jwk-set-uri 指向 Auth 内网端点); CSRF 对无 Cookie 的
 * 令牌认证无意义, 维持关闭; claim 与路径前缀校验、内部身份头注入由链后的
 * 全局过滤器完成; permit-all 逃生口仅供本地无 Auth 联调
 */
@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties::class, GatewayInternalProperties::class)
class GatewaySecurityConfig(
    private val gatewaySecurityProperties: GatewaySecurityProperties,
) {
    /**
     * 构建网关安全过滤链
     *
     * @param http WebFlux 安全构建器, 由 Spring Security 装配传入
     * @return 公开路径放行、其余要求 JWT 认证的安全过滤链
     */
    @Bean
    fun gatewaySecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val enforce = gatewaySecurityProperties.mode == GatewaySecurityProperties.Mode.ENFORCE
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                gatewaySecurityProperties.publicPaths.forEach { pattern ->
                    exchanges.pathMatchers(HttpMethod.OPTIONS, pattern).permitAll()
                    exchanges.pathMatchers(pattern).permitAll()
                }
                if (enforce) {
                    exchanges.anyExchange().authenticated()
                } else {
                    exchanges.anyExchange().permitAll()
                }
            }
            .oauth2ResourceServer { it.jwt { } }
            .build()
    }
}

package com.zax.aspen.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * 网关安全链的临时放行配置
 *
 * gateway-runtime 组合自带 Spring Security, 本期只建设动态路由分发, 令牌校验、公开
 * 路径与路由级权限判断随路线图 Spring Security 步骤在本链上集中落地; 此前保持全部
 * 放行, 依赖部署层网络隔离兜底
 */
@Configuration
class GatewaySecurityConfig {
    /**
     * 构建全部放行的网关安全过滤链
     *
     * 关闭 CSRF 并对所有交换放行, 令牌校验与路由级权限判断由后续版本在本链上集中落地
     *
     * @param http WebFlux 安全构建器, 由 Spring Security 装配传入
     * @return 已禁用 CSRF 且全部放行的安全过滤链
     */
    @Bean
    fun gatewaySecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
            .build()
}

package com.zax.aspen.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * 令牌端类型与路径前缀的双向校验过滤器
 *
 * 技术架构 14.2: 令牌 client_kind claim 与受众路径前缀必须匹配——管理端令牌
 * 只能打 /admin-api, app 令牌只能打 /app-api, 设备令牌只能打 /device-api,
 * 两套用户域 (管理端 UPM 与未来 app 用户域) 靠本过滤永不串门; THIRD_PARTY
 * 令牌的开放能力前缀未定, 不在映射内, 命中任何受管前缀即 403; 校验在安全链
 * 之后执行, 到达本过滤器的请求均已通过 JWT 验签
 */
@Component
class AudienceMatchFilter : GlobalFilter, Ordered {
    /**
     * 校验已认证请求的端类型与路径前缀匹配
     *
     * @param exchange 当前网关交换
     * @param chain 网关过滤器链
     * @return 匹配时继续链, 不匹配时以 403 终止
     */
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        val requiredKind = KIND_BY_PREFIX.entries.firstOrNull { path.startsWith(it.key) }?.value
            ?: return chain.filter(exchange)
        // Mono<Void> 完成即空, switchIfEmpty 会把放行/拒绝误判为无主体, 用 Optional 携带存在性保证分支恰好执行一次
        return exchange.getPrincipal<java.security.Principal>()
            .map { principal -> java.util.Optional.ofNullable(principal as? Authentication) }
            .defaultIfEmpty(java.util.Optional.empty())
            .flatMap { authentication -> dispatch(exchange, chain, authentication.orElse(null), requiredKind) }
    }

    /**
     * 按认证主体分派: 无主体 (公开路径) 放行, 端类型匹配放行, 不匹配拒绝
     *
     * @param exchange 当前网关交换
     * @param chain 网关过滤器链
     * @param authentication 认证主体, 公开路径上无主体时为 null
     * @param requiredKind 路径前缀要求的端类型
     * @return 放行继续链, 不匹配时以 403 终止
     */
    private fun dispatch(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
        authentication: Authentication?,
        requiredKind: String,
    ): Mono<Void> = when {
        authentication == null -> chain.filter(exchange)
        clientKindOf(authentication) == requiredKind -> chain.filter(exchange)
        else -> reject(exchange)
    }

    /** 公开路径与安全链之后执行, 身份头注入之前完成阻断 */
    override fun getOrder(): Int = ORDER

    /**
     * 从认证主体提取 client_kind claim
     *
     * @param authentication 安全链产出的认证主体
     * @return 端类型 claim 值, 令牌缺失该 claim 时返回 null (不匹配任何前缀)
     */
    private fun clientKindOf(authentication: Authentication): String? {
        val jwt = (authentication.credentials as? Jwt) ?: (authentication.principal as? Jwt)
        return jwt?.getClaimAsString(CLIENT_KIND_CLAIM)
    }

    /**
     * 构造 403 拒绝响应
     *
     * @param exchange 当前网关交换
     * @return 已写入拒绝响应的完成信号
     */
    private fun reject(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"code":"COMMON.FORBIDDEN","detail":"令牌端类型与目标路径不匹配"}"""
            .toByteArray(Charsets.UTF_8)
        return exchange.response.writeWith(
            Mono.just(exchange.response.bufferFactory().wrap(body)),
        )
    }

    private companion object {
        /** 过滤器序: 安全链 (默认 -100) 之后, 身份头注入之前 */
        const val ORDER = 100

        /** 端类型 claim 名, 与 AuthTokenService 签发对齐 */
        const val CLIENT_KIND_CLAIM = "client_kind"

        /** 路径前缀到端类型的映射; THIRD_PARTY 的开放前缀未定, 不在此放行 */
        val KIND_BY_PREFIX: Map<String, String> = mapOf(
            "/admin-api" to "admin",
            "/app-api" to "app",
            "/device-api" to "device",
        )
    }
}

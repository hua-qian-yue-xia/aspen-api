package com.zax.aspen.gateway.filter

import com.zax.aspen.common.core.constant.TenantHttpHeaders
import com.zax.aspen.common.core.constant.UserHttpHeaders
import com.zax.aspen.gateway.config.GatewayInternalProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * 内部身份头的剥离与重注入过滤器
 *
 * 技术架构 14.2: 网关先剥离外部传入的全部 X-Aspen-* 内部身份头 (伪造防线),
 * 再按已验签令牌的 claims 重新注入用户/端类型/租户身份, 并携带网关信任凭据
 * (X-Aspen-Gateway-Trust, 值来自 ASPEN_GATEWAY_INTERNAL_SECRET, 与业务侧
 * common-security 的 InternalTrustFilter 比对); 信任凭据未配置时不注入身份头,
 * 业务侧 fail-closed 拒绝携带身份的请求, 防止未接线就误信
 */
@Component
class InternalIdentityHeadersFilter(
    private val gatewayInternalProperties: GatewayInternalProperties,
) : GlobalFilter, Ordered {
    /**
     * 剥离外部身份头并按令牌重注入
     *
     * @param exchange 当前网关交换
     * @param chain 网关过滤器链
     * @return 注入完成后的链信号
     */
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request = exchange.request.mutate().apply {
            STRIPPED_HEADERS.forEach { header -> headers { it.remove(header) } }
        }.build()
        val mutated = exchange.mutate().request(request).build()
        // Mono<Void> 完成即空, 用 Optional 携带主体存在性保证注入分支恰好执行一次
        return mutated.getPrincipal<java.security.Principal>()
            .map { principal -> java.util.Optional.ofNullable(principal as? Authentication) }
            .defaultIfEmpty(java.util.Optional.empty())
            .flatMap { authentication -> chain.filter(injectIdentity(mutated, authentication.orElse(null))) }
    }

    /** 端类型校验之后执行, 尽量贴近转发 */
    override fun getOrder(): Int = ORDER

    /**
     * 把令牌身份与网关信任凭据写入请求头
     *
     * @param exchange 已剥离外部身份头的网关交换
     * @param authentication 已验签的认证主体, 匿名或非 JWT 主体时不注入
     * @return 完成注入的网关交换
     */
    private fun injectIdentity(exchange: ServerWebExchange, authentication: Authentication?): ServerWebExchange {
        if (authentication == null) {
            return exchange
        }
        val jwt = (authentication.credentials as? Jwt) ?: (authentication.principal as? Jwt) ?: return exchange
        val trustToken = gatewayInternalProperties.trustToken
        if (trustToken.isNullOrBlank()) {
            return exchange
        }
        val request = exchange.request.mutate().apply {
            headers { headers ->
                jwt.getClaimAsString("sub")?.let { headers.set(UserHttpHeaders.USER_ID, it) }
                jwt.getClaimAsString(CLIENT_KIND_CLAIM)?.let { headers.set(UserHttpHeaders.CLIENT_KIND, it) }
                jwt.getClaimAsString(TENANT_ID_CLAIM)?.let { headers.set(TenantHttpHeaders.TENANT_ID, it) }
                headers.set(UserHttpHeaders.GATEWAY_TRUST_TOKEN, trustToken)
            }
        }.build()
        return exchange.mutate().request(request).build()
    }

    /** 需要剥离的内部身份头, 客户端传入值一律无效 */
    private val STRIPPED_HEADERS: List<String> = listOf(
        UserHttpHeaders.USER_ID,
        UserHttpHeaders.CLIENT_KIND,
        TenantHttpHeaders.TENANT_ID,
        UserHttpHeaders.GATEWAY_TRUST_TOKEN,
    )

    private companion object {
        /** 过滤器序: 端类型校验 (100) 之后 */
        const val ORDER = 110

        /** 端类型 claim 名 */
        const val CLIENT_KIND_CLAIM = "client_kind"

        /** 租户 claim 名 */
        const val TENANT_ID_CLAIM = "tenant_id"
    }
}

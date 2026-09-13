package com.zax.aspen.gateway.filter

import com.zax.aspen.common.core.constant.TenantHttpHeaders
import com.zax.aspen.common.core.constant.UserHttpHeaders
import com.zax.aspen.gateway.config.GatewayInternalProperties
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 覆盖内部身份头剥离与按令牌重注入的语义 */
class InternalIdentityHeadersFilterTest {
    /** 验证外部伪造身份头被剥离, 并按令牌 claims 与信任凭据重注入 */
    @Test
    fun `strips forged headers and injects identity from token`() {
        val filter = InternalIdentityHeadersFilter(GatewayInternalProperties(trustToken = "trust-secret"))
        val exchange = principalExchange(
            "/admin-api/sys/route/list",
            extraHeaders = mapOf(
                UserHttpHeaders.USER_ID to "999",
                TenantHttpHeaders.TENANT_ID to "888",
                UserHttpHeaders.GATEWAY_TRUST_TOKEN to "forged",
            ),
            authentication = token(),
        )
        val recorder = recordingChain()

        filter.filter(exchange, recorder.chain).block()

        val forwarded = requireNotNull(recorder.forwarded.get())
        assertEquals("42", forwarded.request.headers.getFirst(UserHttpHeaders.USER_ID))
        assertEquals("admin", forwarded.request.headers.getFirst(UserHttpHeaders.CLIENT_KIND))
        assertEquals("7", forwarded.request.headers.getFirst(TenantHttpHeaders.TENANT_ID))
        assertEquals("trust-secret", forwarded.request.headers.getFirst(UserHttpHeaders.GATEWAY_TRUST_TOKEN))
    }

    /** 验证信任凭据未配置时不注入任何身份头 (业务侧 fail-closed 兜底) */
    @Test
    fun `does not inject identity when trust token unconfigured`() {
        val filter = InternalIdentityHeadersFilter(GatewayInternalProperties(trustToken = null))
        val exchange = principalExchange(
            "/admin-api/sys/route/list",
            extraHeaders = mapOf(UserHttpHeaders.USER_ID to "999"),
            authentication = token(),
        )
        val recorder = recordingChain()

        filter.filter(exchange, recorder.chain).block()

        val forwarded = requireNotNull(recorder.forwarded.get())
        assertNull(forwarded.request.headers.getFirst(UserHttpHeaders.USER_ID))
        assertNull(forwarded.request.headers.getFirst(UserHttpHeaders.GATEWAY_TRUST_TOKEN))
    }

    /** 验证无认证主体时仅完成剥离, 不注入 */
    @Test
    fun `strips only for anonymous request`() {
        val filter = InternalIdentityHeadersFilter(GatewayInternalProperties(trustToken = "trust-secret"))
        val exchange = principalExchange(
            "/actuator/health",
            extraHeaders = mapOf(UserHttpHeaders.USER_ID to "999"),
            authentication = null,
        )
        val recorder = recordingChain()

        filter.filter(exchange, recorder.chain).block()

        val forwarded = requireNotNull(recorder.forwarded.get())
        assertNull(forwarded.request.headers.getFirst(UserHttpHeaders.USER_ID))
    }

    /**
     * 构造网关交换, 认证主体与请求头可控
     *
     * MockServerWebExchange 的 mutate().principal 在当前版本不回读, 用类委托装饰固定主体
     *
     * @param path 请求路径
     * @param extraHeaders 附加请求头
     * @param authentication 认证主体, null 表示匿名
     * @return 固定返回该主体 (或空) 的网关交换
     */
    private fun principalExchange(
        path: String,
        extraHeaders: Map<String, String>,
        authentication: org.springframework.security.core.Authentication?,
    ): ServerWebExchange {
        val requestBuilder = MockServerHttpRequest.get(path)
        extraHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
        val delegate = MockServerWebExchange.from(requestBuilder.build())
        return object : ServerWebExchange by delegate {
            @Suppress("UNCHECKED_CAST")
            override fun <T : java.security.Principal> getPrincipal(): Mono<T> =
                if (authentication == null) Mono.empty() else Mono.just(authentication as T)
        }
    }

    /**
     * 构造记录放行交换的过滤器链 mock 与记录器
     *
     * @return 持有链 mock 与放行交换记录的记录器
     */
    private fun recordingChain(): Recorder {
        val recorder = Recorder()
        Mockito.`when`(recorder.chain.filter(ArgumentMatchers.any<ServerWebExchange>()))
            .thenAnswer { invocation ->
                recorder.forwarded.set(invocation.getArgument<ServerWebExchange>(0))
                Mono.empty<Void>()
            }
        return recorder
    }

    /** 放行交换记录持有者 */
    private class Recorder {
        /** 被包装的链 mock */
        val chain: GatewayFilterChain = Mockito.mock(GatewayFilterChain::class.java)

        /** 最近一次放行的交换 */
        val forwarded = ThreadLocal<ServerWebExchange?>()
    }

    /**
     * 构造携带身份 claims 的 JWT 认证主体
     *
     * @return 以 JWT 为凭据的认证主体
     */
    private fun token(): org.springframework.security.core.Authentication {
        val now = Instant.now()
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("42")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .claim("client_kind", "admin")
            .claim("tenant_id", "7")
            .build()
        return JwtAuthenticationToken(jwt, emptyList())
    }
}

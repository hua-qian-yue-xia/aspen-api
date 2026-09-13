package com.zax.aspen.gateway.filter

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 覆盖令牌端类型与路径前缀双向校验的放行与阻断语义 */
class AudienceMatchFilterTest {
    /** 验证管理端令牌访问管理端前缀放行 */
    @Test
    fun `allows admin token on admin prefix`() {
        val exchange = principalExchange("/admin-api/sys/route/list", adminToken())
        val chain = recordingChain()

        AudienceMatchFilter().filter(exchange, chain).block()

        assertNotNull(chain.invokedExchange())
    }

    /** 验证管理端令牌访问 app 前缀被 403 阻断 */
    @Test
    fun `rejects admin token on app prefix`() {
        val exchange = principalExchange("/app-api/user/profile", adminToken())
        val chain = recordingChain()

        AudienceMatchFilter().filter(exchange, chain).block()

        assertNull(chain.invokedExchange())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    /** 验证缺失 client_kind claim 的令牌不匹配任何受管前缀 */
    @Test
    fun `rejects token without client kind claim on managed prefix`() {
        val exchange = principalExchange("/admin-api/sys/route/list", token(kindClaim = null))
        val chain = recordingChain()

        AudienceMatchFilter().filter(exchange, chain).block()

        assertNull(chain.invokedExchange())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    /** 验证非受管前缀 (无端类型映射) 的请求直接放行 */
    @Test
    fun `passes through unmanaged prefix without principal`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build())
        val chain = recordingChain()

        AudienceMatchFilter().filter(exchange, chain).block()

        assertNotNull(chain.invokedExchange())
    }

    /**
     * 构造携带指定认证主体的网关交换
     *
     * MockServerWebExchange 的 mutate().principal 在当前版本不回读, 用类委托装饰固定主体
     *
     * @param path 请求路径
     * @param authentication 认证主体
     * @return 固定返回该主体的网关交换
     */
    private fun principalExchange(path: String, authentication: Authentication): ServerWebExchange {
        val delegate = MockServerWebExchange.from(MockServerHttpRequest.get(path).build())
        return object : ServerWebExchange by delegate {
            @Suppress("UNCHECKED_CAST")
            override fun <T : java.security.Principal> getPrincipal(): Mono<T> = Mono.just(authentication as T)
        }
    }

    /**
     * 构造记录放行交换的过滤器链 mock
     *
     * @return 放行时记录入参并立即完成的链替身
     */
    private fun recordingChain(): GatewayFilterChain {
        val chain = Mockito.mock(GatewayFilterChain::class.java)
        Mockito.`when`(chain.filter(ArgumentMatchers.any<ServerWebExchange>()))
            .thenAnswer { invocation ->
                forwarded.set(invocation.getArgument<ServerWebExchange>(0))
                Mono.empty<Void>()
            }
        return chain
    }

    /** 最近一次放行的交换 */
    private val forwarded = ThreadLocal<ServerWebExchange?>()

    /**
     * 读取链替身记录的放行交换
     *
     * @return 最近一次放行的交换, 未放行为 null
     */
    private fun GatewayFilterChain.invokedExchange(): ServerWebExchange? = forwarded.get()

    /**
     * 构造携带管理端 claim 的认证令牌
     *
     * @return JWT 认证主体
     */
    private fun adminToken(): Authentication = token("admin")

    /**
     * 构造 JWT 认证主体
     *
     * @param kindClaim 端类型 claim 值, null 表示令牌缺失该 claim
     * @return 以 JWT 为凭据的认证主体
     */
    private fun token(kindClaim: String?): Authentication {
        val now = Instant.now()
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("42")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .apply { if (kindClaim != null) claim("client_kind", kindClaim) }
            .build()
        return JwtAuthenticationToken(jwt, emptyList())
    }
}

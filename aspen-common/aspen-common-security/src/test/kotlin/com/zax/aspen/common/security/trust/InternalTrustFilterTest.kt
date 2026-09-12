package com.zax.aspen.common.security.trust

import com.zax.aspen.common.core.constant.TenantHttpHeaders
import com.zax.aspen.common.core.constant.UserHttpHeaders
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 覆盖最小信任链的放行、拒绝与上下文生命周期语义 */
class InternalTrustFilterTest {
    /** 验证无身份头的匿名请求直接放行, 不要求信任凭据 */
    @Test
    fun `passes through anonymous request without trust requirement`() {
        val chain = RecordingFilterChain()
        val response = MockHttpServletResponse()

        InternalTrustFilter(expectedTrustToken = "trust-secret")
            .doFilter(MockHttpServletRequest(), response, chain)

        assertTrue(chain.invoked)
        assertEquals(200, response.status)
        assertNull(RequestIdentityContext.get())
    }

    /** 验证信任头匹配时身份进入请求上下文, 请求结束后清理 */
    @Test
    fun `populates identity when trust token matches and clears afterwards`() {
        val chain = RecordingFilterChain()
        val request = MockHttpServletRequest()
        request.addHeader(UserHttpHeaders.USER_ID, "42")
        request.addHeader(UserHttpHeaders.CLIENT_KIND, "admin")
        request.addHeader(TenantHttpHeaders.TENANT_ID, "7")
        request.addHeader(UserHttpHeaders.GATEWAY_TRUST_TOKEN, "trust-secret")

        InternalTrustFilter(expectedTrustToken = "trust-secret")
            .doFilter(request, MockHttpServletResponse(), chain)

        assertTrue(chain.invoked)
        val identity = chain.capturedIdentity
        assertEquals(42L, identity?.userId)
        assertEquals("admin", identity?.clientKind)
        assertEquals(7L, identity?.tenantId)
        assertNull(RequestIdentityContext.get())
    }

    /** 验证信任缺失、不符或进程未配置凭据时携带身份头的请求一律 401 拒绝 */
    @Test
    fun `rejects identity headers when trust is missing or mismatched`() {
        val request = MockHttpServletRequest()
        request.addHeader(UserHttpHeaders.USER_ID, "42")

        val noTrust = MockHttpServletResponse()
        InternalTrustFilter(expectedTrustToken = "trust-secret")
            .doFilter(request, noTrust, RecordingFilterChain())
        assertEquals(401, noTrust.status)

        val wrongTrust = MockHttpServletResponse()
        request.addHeader(UserHttpHeaders.GATEWAY_TRUST_TOKEN, "wrong")
        InternalTrustFilter(expectedTrustToken = "trust-secret")
            .doFilter(request, wrongTrust, RecordingFilterChain())
        assertEquals(401, wrongTrust.status)

        val unconfigured = MockHttpServletResponse()
        val trustedRequest = MockHttpServletRequest()
        trustedRequest.addHeader(UserHttpHeaders.USER_ID, "42")
        trustedRequest.addHeader(UserHttpHeaders.GATEWAY_TRUST_TOKEN, "trust-secret")
        InternalTrustFilter(expectedTrustToken = null)
            .doFilter(trustedRequest, unconfigured, RecordingFilterChain())
        assertEquals(401, unconfigured.status)
    }

    /** 验证身份头格式非法 (主体或租户非数字) 视为伪造并拒绝 */
    @Test
    fun `rejects malformed identity headers even with valid trust`() {
        val request = MockHttpServletRequest()
        request.addHeader(UserHttpHeaders.USER_ID, "not-a-number")
        request.addHeader(UserHttpHeaders.GATEWAY_TRUST_TOKEN, "trust-secret")

        val response = MockHttpServletResponse()
        InternalTrustFilter(expectedTrustToken = "trust-secret")
            .doFilter(request, response, RecordingFilterChain())

        assertEquals(401, response.status)
        assertFalse(response.errorMessage.isNullOrBlank())
    }

    /**
     * 记录调用与身份快照的过滤器链替身
     *
     * 在链内读取请求上下文并保存副本, 供测试断言「链执行期间身份可见、结束后清理」
     */
    private class RecordingFilterChain : FilterChain {
        /** 链是否被调用 */
        var invoked: Boolean = false
            private set

        /** 链执行期间捕获的身份快照 */
        var capturedIdentity: RequestIdentityContext.Identity? = null
            private set

        /**
         * 记录调用并捕获当时的身份
         *
         * @param request 当前请求
         * @param response 当前响应
         */
        override fun doFilter(request: jakarta.servlet.ServletRequest, response: jakarta.servlet.ServletResponse) {
            invoked = true
            capturedIdentity = RequestIdentityContext.get()
        }
    }
}

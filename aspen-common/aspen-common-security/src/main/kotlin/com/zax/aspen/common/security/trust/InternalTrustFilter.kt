package com.zax.aspen.common.security.trust

import com.zax.aspen.common.core.constant.TenantHttpHeaders
import com.zax.aspen.common.core.constant.UserHttpHeaders
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 业务进程的最小信任链过滤器: 拒绝绕过网关的伪造身份头
 *
 * 技术架构 14.2: 网关剥离外部传入的 X-Aspen-* 身份头后按令牌重新注入, 并携带
 * 信任凭据 (X-Aspen-Gateway-Trust, 值来自 ASPEN_GATEWAY_INTERNAL_SECRET);
 * 本过滤器在校验信任头后才把身份头解析进 [RequestIdentityContext], 信任缺失、
 * 不符或身份头格式非法即 401 拒绝——内网直连业务端口 + 手工带头无法冒充用户;
 * 未携带任何身份头的请求 (匿名/内部探活) 直接放行, 不做信任要求
 *
 * @param expectedTrustToken 本进程期望的网关信任凭据, 为 null 或空白表示进程
 * 未配置凭据, 此时任何身份头请求都被拒绝 (fail-closed, 防止未接线就误信)
 */
class InternalTrustFilter(
    private val expectedTrustToken: String?,
) : OncePerRequestFilter() {
    /**
     * 校验信任头并把合法身份写入请求上下文
     *
     * @param request 当前请求
     * @param response 当前响应, 拒绝路径上写入 401
     * @param filterChain 过滤器链
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rawUserId = request.getHeader(UserHttpHeaders.USER_ID)
        val rawClientKind = request.getHeader(UserHttpHeaders.CLIENT_KIND)
        val rawTenantId = request.getHeader(TenantHttpHeaders.TENANT_ID)
        if (rawUserId == null && rawClientKind == null && rawTenantId == null) {
            filterChain.doFilter(request, response)
            return
        }
        val identity = parseIdentity(rawUserId, rawClientKind, rawTenantId)
        if (identity == null || !trustMatches(request)) {
            RequestIdentityContext.clear()
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "internal identity rejected")
            return
        }
        try {
            RequestIdentityContext.set(identity)
            filterChain.doFilter(request, response)
        } finally {
            RequestIdentityContext.clear()
        }
    }

    /**
     * 比对请求携带的网关信任凭据
     *
     * @param request 当前请求
     * @return 进程已配置凭据且请求携带值一致时为 `true`
     */
    private fun trustMatches(request: HttpServletRequest): Boolean {
        if (expectedTrustToken.isNullOrBlank()) {
            return false
        }
        return constantTimeEquals(expectedTrustToken, request.getHeader(UserHttpHeaders.GATEWAY_TRUST_TOKEN))
    }

    /**
     * 解析身份头; 任一非空头格式非法即判定伪造
     *
     * @param rawUserId 用户主体头原文, 可为 null
     * @param rawClientKind 端类型头原文, 可为 null
     * @param rawTenantId 租户头原文, 可为 null
     * @return 解析成功的身份, 存在非法头时返回 `null`
     */
    private fun parseIdentity(rawUserId: String?, rawClientKind: String?, rawTenantId: String?): RequestIdentityContext.Identity? {
        val userId = rawUserId?.trim()?.let { it.toLongOrNull() ?: return null }
        val clientKind = rawClientKind?.trim()?.takeIf { it.isNotEmpty() }
        val tenantId = rawTenantId?.trim()?.let { it.toLongOrNull() ?: return null }
        return RequestIdentityContext.Identity(userId = userId, clientKind = clientKind, tenantId = tenantId)
    }

    /**
     * 常数时间字符串比对, 避免逐字符短路成为计时侧信道
     *
     * @param expected 期望值 (进程配置的信任凭据)
     * @param actual 请求实际携带值, 可为 null
     * @return 长度与内容完全一致时为 `true`
     */
    private fun constantTimeEquals(expected: String, actual: String?): Boolean {
        if (actual == null || expected.length != actual.length) {
            return false
        }
        var result = 0
        for (index in expected.indices) {
            result = result or (expected[index].code xor actual[index].code)
        }
        return result == 0
    }
}

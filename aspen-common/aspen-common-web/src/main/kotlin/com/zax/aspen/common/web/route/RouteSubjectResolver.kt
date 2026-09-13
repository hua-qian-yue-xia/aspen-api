package com.zax.aspen.common.web.route

import com.zax.aspen.common.security.trust.RequestIdentityContext
import jakarta.servlet.http.HttpServletRequest

/**
 * 限流与操作日志共用的请求主体解析器
 *
 * 主体优先取 common-security 信任链建立的请求级身份 (RequestIdentityContext, 网关是唯一
 * 合法写入方), 未接入 common-security 或匿名请求回退 `X-Forwarded-For` 首跳 (仅网关链路
 * 可信, 直连服务端口伪造 XFF 可稀释限流桶, 由内网边界兜底), 再回退匿名共享桶;
 * 本类引用 common-security 类型, 因此只在 security 位于类路径时条件装配
 */
class RouteSubjectResolver {
    /**
     * 解析当前请求的限流主体键
     *
     * @param request 当前请求
     * @return 形如 `u:{clientKind}:{userId}`、`ip:{来源IP}` 或 `anonymous` 的主体键
     */
    fun subject(request: HttpServletRequest): String {
        val identity = RequestIdentityContext.get()
        if (identity?.userId != null) {
            return "u:${identity.clientKind ?: "none"}:${identity.userId}"
        }
        val forwardedFor = request.getHeader(FORWARDED_FOR_HEADER)
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
        if (!forwardedFor.isNullOrBlank()) {
            return "ip:$forwardedFor"
        }
        return "anonymous"
    }

    private companion object {
        /** 网关链路的标准转发头, 多跳逗号分隔, 取首跳即最初调用方地址 */
        private const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
    }
}

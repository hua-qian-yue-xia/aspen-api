package com.zax.aspen.common.web.route

import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import com.zax.aspen.common.route.RateLimitScope
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * 路由套件的限流拦截器
 *
 * 只对携带路由注解的端点生效: 解析限流声明后按「映射 pattern + HTTP 方法 + scope 主体」
 * 构造 key, 在进程内固定窗口计数; 超限抛 BusinessException(TOO_MANY_REQUESTS),
 * 经统一异常契约渲染 429 application/problem+json (拦截器 preHandle 抛出的异常与
 * Controller 异常走同一 HandlerExceptionResolver 链)。注册序位于操作日志拦截器之后,
 * 被限流拒绝的请求仍产出操作日志行; 未注解端点直接放行, 零额外开销
 */
class RateLimitInterceptor(
    private val routeSpecResolver: RouteSpecResolver,
    private val rateLimiter: FixedWindowRateLimiter,
    private val subjectResolver: RouteSubjectResolver?,
) : HandlerInterceptor {
    /**
     * 限流闸门
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 当前 handler, 仅处理 HandlerMethod
     * @return 未注解端点或配额内返回 `true` 放行; 超限以异常中断, 不返回 `false`
     */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }
        val rateLimit = routeSpecResolver.resolve(handler)?.rateLimit ?: return true
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String?
        val subject = when (rateLimit.scope) {
            RateLimitScope.USER -> subjectResolver?.subject(request) ?: "anonymous"
            RateLimitScope.GLOBAL -> "all"
        }
        val key = "${request.method}|${pattern ?: request.requestURI}|${rateLimit.scope}:$subject"
        val allowed = rateLimiter.tryAcquire(key, rateLimit.limit, rateLimit.windowSeconds)
        if (!allowed) {
            throw BusinessException(
                CommonErrorCode.TOO_MANY_REQUESTS,
                "请求过于频繁, 请 ${rateLimit.windowSeconds} 秒后重试",
            )
        }
        return true
    }
}

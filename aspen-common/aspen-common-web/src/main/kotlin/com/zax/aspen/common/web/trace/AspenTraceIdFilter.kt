package com.zax.aspen.common.web.trace

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 为每个请求建立 Trace ID 并关联日志
 *
 * 进入请求时读取 [TraceId.HEADER] (合法则复用网关或调用方透传值, 非法或缺失则本地生成),
 * 写入 MDC 供日志模式取值, 并在响应头回写同一标识; 所有响应 (成功与失败) 都携带
 * [TraceId.HEADER], 前端从响应头即可取得排查标识。注册序必须为最高优先级,
 * 保证 MDC 覆盖完整请求周期 (含后续 Filter 与异常处理器的日志)。
 * 本过滤器按同步请求模型设计: `OncePerRequestFilter` 默认跳过异步再分发,
 * 若未来引入 Callable/DeferredResult 等异步 Controller, 完成线程与再分发阶段
 * MDC 将无 traceId, 届时须覆写 shouldNotFilterAsyncDispatch 返回 false 或另行改造
 */
class AspenTraceIdFilter : OncePerRequestFilter() {

    /**
     * 读或生成 traceId, 写 MDC 与响应头后放行请求, 结束时清理 MDC
     *
     * finally 清理防止线程池复用导致下一个请求串到上一请求的 traceId
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param filterChain 过滤器链
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = TraceId.sanitize(request.getHeader(TraceId.HEADER)) ?: TraceId.generate()
        MDC.put(TraceId.MDC_KEY, traceId)
        // 响应头在放行前置位, 即使下游立即失败也保证排查标识可达调用方
        response.setHeader(TraceId.HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TraceId.MDC_KEY)
        }
    }
}

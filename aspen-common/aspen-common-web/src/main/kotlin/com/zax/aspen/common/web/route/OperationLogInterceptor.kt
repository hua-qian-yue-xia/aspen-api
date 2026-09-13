package com.zax.aspen.common.web.route

import com.zax.aspen.common.web.trace.TraceId
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * 路由套件的操作日志拦截器
 *
 * 对携带路由注解的端点, 在请求完成时以独立 logger 名 `aspen.operation` 输出一条 INFO
 * 单行结构化日志 (tag/summary/method/pattern/status/costMs/subject/traceId/异常摘要),
 * 运维可按 logger 名独立路由到访问日志采集或降级; 注册序位于限流拦截器之前,
 * 被限流拒绝 (429) 与抛错的请求同样产出日志行。请求/响应体捕获与落库属二期,
 * 当前不读取报文体, 无内存放大风险
 */
class OperationLogInterceptor(
    private val routeSpecResolver: RouteSpecResolver,
    private val subjectResolver: RouteSubjectResolver?,
) : HandlerInterceptor {
    /**
     * 记录请求起点纳秒, 供完成时计算耗时
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 当前 handler, 仅 HandlerMethod 记录起点
     * @return 恒为 `true`, 本拦截器不做闸门
     */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime())
        }
        return true
    }

    /**
     * 输出操作日志行
     *
     * @param request 当前请求
     * @param response 当前响应, status 字段取其状态码
     * @param handler 当前 handler, 仅处理 HandlerMethod
     * @param exception handler 抛出的异常, 成功请求为 `null`
     */
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        exception: Exception?,
    ) {
        if (handler !is HandlerMethod) {
            return
        }
        val spec = routeSpecResolver.resolve(handler) ?: return
        val startNanos = request.getAttribute(START_NANOS_ATTRIBUTE) as? Long ?: return
        val costMillis = (System.nanoTime() - startNanos) / 1_000_000
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String?
        logger.info(
            "tag={} summary={} method={} pattern={} status={} costMs={} subject={} traceId={} error={}",
            spec.log.name,
            spec.summary,
            request.method,
            pattern ?: request.requestURI,
            response.status,
            costMillis,
            subjectResolver?.subject(request) ?: "unknown",
            MDC.get(TraceId.MDC_KEY) ?: "-",
            exception?.message ?: "-",
        )
    }

    private companion object {
        /** 操作日志的独立 logger 名, 运维按该名路由与降级 */
        private const val LOGGER_NAME = "aspen.operation"

        /** preHandle 写入的起点纳秒属性名 */
        private const val START_NANOS_ATTRIBUTE = "aspen.route.operationStartNanos"

        /** 操作日志走独立 logger, 不挂本类名, 便于按名采集与级别控制 */
        private val logger: Logger = LoggerFactory.getLogger(LOGGER_NAME)
    }
}

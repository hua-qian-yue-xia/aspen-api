package com.zax.aspen.common.web.route

import io.swagger.v3.oas.models.Operation
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springframework.web.method.HandlerMethod

/**
 * 路由套件的 springdoc 定制
 *
 * 把路由注解的 summary/description 写入 OpenAPI Operation; 返回值结构由 springdoc 从
 * handler 方法签名的强类型直接推断 (如 PageResult<TaskVO>), 路由注解不声明响应包装。
 * 非文档属性 (rateLimit/log) 不进文档; 未注解端点原样返回
 */
class RouteOperationCustomizer(
    private val routeSpecResolver: RouteSpecResolver,
) : GlobalOperationCustomizer {
    /**
     * 补写文档元信息
     *
     * @param operation springdoc 为当前 handler 构建的 Operation
     * @param handlerMethod 当前 handler 方法
     * @return 补写 summary/description 后的 Operation; 未注解端点原样返回
     */
    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val spec = routeSpecResolver.resolve(handlerMethod) ?: return operation
        operation.summary(spec.summary)
        if (spec.description.isNotBlank()) {
            operation.description(spec.description)
        }
        return operation
    }
}

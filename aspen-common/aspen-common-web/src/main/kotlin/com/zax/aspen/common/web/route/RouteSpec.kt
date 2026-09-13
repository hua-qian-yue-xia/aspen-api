package com.zax.aspen.common.web.route

import com.zax.aspen.common.route.OperationTag
import com.zax.aspen.common.route.RateLimitSpec
import kotlin.reflect.KClass

/**
 * 端点路由声明的解析结果
 *
 * [RouteSpecResolver] 把五个 verb 组合注解 (GetRoute/PostRoute/PutRoute/DeleteRoute/PatchRoute)
 * 归一为本结构, 消费端 (限流拦截器、操作日志拦截器、springdoc 定制) 不感知具体注解类型
 *
 * @property verb 命中的 verb 组合注解类型, 即端点的 HTTP 方法声明来源
 * @property summary 接口用途摘要, 进 springdoc 文档与操作日志
 * @property description 接口用途补充说明, 空串时不写入文档
 * @property rateLimit 限流声明, 语义见 RateLimitSpec
 * @property log 操作日志分类标签
 */
data class RouteSpec(
    val verb: KClass<out Annotation>,
    val summary: String,
    val description: String,
    val rateLimit: RateLimitSpec,
    val log: OperationTag,
)

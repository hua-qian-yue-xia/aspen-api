package com.zax.aspen.common.route

import org.springframework.core.annotation.AliasFor
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * PUT 端点的路由套件声明
 *
 * 属性与消费端与 [PostRoute] 完全一致, 仅 HTTP 方法为 PUT, 语义说明见 [PostRoute]
 *
 * @property path 端点相对路径
 * @property summary 接口用途的一句话摘要, 必填
 * @property description 接口用途的补充说明, 空串时不写入文档
 * @property rateLimit 限流声明, 默认 60 秒 / 100 次 / USER 作用域
 * @property log 操作日志分类标签, 默认 OTHER
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@RequestMapping(method = [RequestMethod.PUT])
annotation class PutRoute(
    @get:AliasFor(annotation = RequestMapping::class, attribute = "path")
    vararg val path: String,
    val summary: String,
    val description: String = "",
    val rateLimit: RateLimitSpec = RateLimitSpec(),
    val log: OperationTag = OperationTag.OTHER,
)

package com.zax.aspen.common.route

import org.springframework.core.annotation.AliasFor
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * POST 端点的路由套件声明
 *
 * 元标注 `@RequestMapping(method = POST)`, 因此 MVC 映射注册、IDE 跳转与原生 verb 注解
 * 完全一致; [path] 经 `@AliasFor` 转发给 `RequestMapping.path`, 只写受众后的模块相对路径,
 * 受众前缀仍由 Controller 包位置决定 (common-web §8)。注解可声明在 api 契约接口方法上,
 * 实现 Controller 零注解; 全部属性由 common-web 的消费端装配 (§8.3):
 * [summary]/[description] 进 OpenAPI 文档与操作日志, [rateLimit] 驱动进程内限流,
 * [log] 作为操作日志分类标签。每个属性必须有真实消费者, 由 RouteSuiteBoundaryTest 强制;
 * 不引入响应包装声明 (返回值结构由 springdoc 从方法签名推断) 与免鉴权标记 (鉴权权威在网关)
 *
 * @property path 端点相对路径, 如 `/task`; 不写时映射到 Controller 类的基准路径
 * @property summary 接口用途的一句话摘要, 必填, 进 swagger 文档与操作日志
 * @property description 接口用途的补充说明, 空串时不写入文档
 * @property rateLimit 限流声明, 默认 60 秒 / 100 次 / USER 作用域
 * @property log 操作日志分类标签, 默认 OTHER
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@RequestMapping(method = [RequestMethod.POST])
annotation class PostRoute(
    @get:AliasFor(annotation = RequestMapping::class, attribute = "path")
    vararg val path: String,
    val summary: String,
    val description: String = "",
    val rateLimit: RateLimitSpec = RateLimitSpec(),
    val log: OperationTag = OperationTag.OTHER,
)

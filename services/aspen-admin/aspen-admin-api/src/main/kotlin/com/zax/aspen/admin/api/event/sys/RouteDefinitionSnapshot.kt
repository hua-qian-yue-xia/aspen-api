package com.zax.aspen.admin.api.event.sys

import java.net.URI

/**
 * 单条网关路由的发布快照
 *
 * Admin 从 sys_route 构建后写入 Redis 路由信封, Gateway 读取后转换为 Spring Cloud
 * Gateway 的 RouteDefinition; routeCode 直接作为路由 id, order 由 sort_order 映射;
 * 构造时校验 uri 协议与语法及断言存在性, 使非法路由在进入分发介质之前即失败,
 * 避免一条语法非法 uri 在网关映射期抛异常导致该次刷新整体丢失
 */
data class RouteDefinitionSnapshot(
    /** 路由编码, 即 Gateway route id; 小写中划线格式, 全局唯一, 创建后不可修改 */
    val routeCode: String,
    /** 目标地址, 只允许 lb:// (Nacos 服务发现) 或 http://、https:// 直连, 语法非法在构造期拒绝 */
    val uri: String,
    /** 路由匹配顺序, 数值小者先匹配, 由 sys_route.sort_order 映射 */
    val order: Int,
    /** 断言列表, 至少一条; 无断言路由会兜住全部请求, 属于危险配置, 一律拒绝 */
    val predicates: List<RouteDefinitionPart>,
    /** 过滤器列表, 可为空; 依次叠加, 如 StripPrefix、AddRequestHeader */
    val filters: List<RouteDefinitionPart>,
    /** 路由级参数, 如 response-timeout; 只影响网关行为, 不参与业务判断 */
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(routeCode.matches(CODE_PATTERN)) { "路由编码必须为小写中划线格式: $routeCode" }
        require(uri.startsWith("lb://") || uri.startsWith("http://") || uri.startsWith("https://")) {
            "路由目标地址只允许 lb://、http:// 或 https://: $routeCode -> $uri"
        }
        require(runCatching { URI.create(uri) }.isSuccess) {
            "路由目标地址语法非法: $routeCode -> $uri"
        }
        require(predicates.isNotEmpty()) { "路由至少需要一条断言: $routeCode" }
    }

    private companion object {
        /** 校验路由编码的小写中划线格式, 与服务命名风格一致 */
        val CODE_PATTERN = Regex("[a-z][a-z0-9-]*")
    }
}

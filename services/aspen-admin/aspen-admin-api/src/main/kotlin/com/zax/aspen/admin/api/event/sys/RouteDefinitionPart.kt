package com.zax.aspen.admin.api.event.sys

/**
 * 网关路由断言或过滤器的通用结构
 *
 * 与 Spring Cloud Gateway 的 PredicateDefinition、FilterDefinition 同构, 但不依赖
 * 网关类库; name 为 Spring Cloud Gateway 工厂名 (如 Path、StripPrefix), args 为
 * 工厂参数; 同一路由内可重复出现同一工厂, 由声明顺序决定叠加次序
 */
data class RouteDefinitionPart(
    /** Spring Cloud Gateway 断言或过滤器工厂名, 如 Path、Method、StripPrefix */
    val name: String,
    /** 工厂参数, 键以下划线序号 (如 _genkey_0) 或工厂具名参数声明 */
    val args: Map<String, String>,
) {
    init {
        require(name.isNotBlank()) { "断言或过滤器工厂名不得为空" }
    }
}

package com.zax.aspen.common.gateway.contract

/**
 * 网关路由全量分发信封
 *
 * Admin 在路由变更事务提交后与启动时把全部启用路由构建为本信封, 一条 SET 原子替换
 * Redis 路由 Key; version 单调递增, Gateway 与本地版本比对防止乱序刷新;
 * publishedAt 为 ISO-8601 带时区偏移的发布时刻字符串, 仅用于排查, 不参与比对
 */
data class RouteCatalogSnapshot(
    /** 快照版本, 由 Redis 版本计数器 INCR 产生, 单调递增 */
    val version: Long,
    /** 发布时刻, ISO-8601 字符串, 仅用于运维排查 */
    val publishedAt: String,
    /** 全部启用路由, 按匹配顺序排列; 路由编码在信封内必须唯一 */
    val routes: List<RouteDefinitionSnapshot>,
) {
    init {
        require(version > 0) { "路由快照版本必须为正数: $version" }
        require(publishedAt.isNotBlank()) { "路由快照必须携带发布时刻" }
        val duplicatedCodes = routes.groupBy { it.routeCode }.filterValues { it.size > 1 }.keys
        require(duplicatedCodes.isEmpty()) { "路由快照存在重复编码: $duplicatedCodes" }
    }
}

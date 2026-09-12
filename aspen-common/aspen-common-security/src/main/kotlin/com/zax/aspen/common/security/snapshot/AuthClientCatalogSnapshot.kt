package com.zax.aspen.common.security.snapshot

/**
 * 认证客户端配置全量分发信封
 *
 * Admin 在配置变更事务提交后与启动时把全部启用端及其方式行构建为本信封, 版本守卫
 * 落盘 Redis (仅当新于在途版本才替换); version 单调递增, Auth 与本地版本比对防止
 * 乱序刷新; publishedAt 为 ISO-8601 带时区偏移的发布时刻字符串, 仅用于排查,
 * 不参与比对
 */
data class AuthClientCatalogSnapshot(
    /** 快照版本, 由 Redis 版本计数器 INCR 产生, 单调递增 */
    val version: Long,
    /** 发布时刻, ISO-8601 字符串, 仅用于运维排查 */
    val publishedAt: String,
    /** 全部启用端及其登录方式; 端编码在信封内必须唯一 */
    val clients: List<AuthClientSnapshot>,
) {
    init {
        require(version > 0) { "客户端配置快照版本必须为正数: $version" }
        require(publishedAt.isNotBlank()) { "客户端配置快照必须携带发布时刻" }
        val duplicatedCodes = clients.groupBy { it.clientCode }.filterValues { it.size > 1 }.keys
        require(duplicatedCodes.isEmpty()) { "客户端配置快照存在重复端编码: $duplicatedCodes" }
    }
}

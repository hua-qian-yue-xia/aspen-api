package com.zax.aspen.common.gateway.consume

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.gateway.GatewayRouteProperties
import com.zax.aspen.common.gateway.contract.GatewayRouteContract
import com.zax.aspen.common.gateway.contract.RouteCatalogSnapshot
import com.zax.aspen.common.gateway.contract.RouteDefinitionSnapshot
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

/**
 * 从 Redis 加载并持有网关路由快照的唯一入口
 *
 * 只在启动与收到刷新通知时读一次 Redis, 运行期路由匹配全部走内存快照, Redis 故障
 * 不影响已加载路由; Redis 访问经 common-cache 的 AspenRedisOperations 分发原语;
 * 解析采用两段式: 信封级损坏保留旧快照并告警, 单条路由损坏跳过并告警, 不阻塞整体
 * 刷新; 版本比对由调用方先行完成, 本类只负责加载与持有
 *
 * 与仓库通用契约的差异: 本类是 common-gateway 的工厂装配基础设施, 协作依赖经
 * 构造参数注入, 由 AspenGatewayAutoConfiguration 统一装配
 *
 * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
 * @param objectMapper Jackson 3 mapper, 用于信封与单条路由的反序列化
 * @param properties 路由分发配置, environment 决定读取的 Redis Key
 */
class RouteSnapshotStore(
    private val aspenRedisOperations: AspenRedisOperations,
    private val objectMapper: ObjectMapper,
    private val properties: GatewayRouteProperties,
) {
    @Volatile
    private var current: RouteCatalogSnapshot? = null

    /** 当前持有的快照版本, 未加载过为 null */
    val version: Long?
        get() = current?.version

    /**
     * 当前持有的路由快照, 未加载过为空列表
     *
     * @return 内存快照中的全部路由定义, 未加载过或快照为空时返回空列表
     */
    fun currentSnapshots(): List<RouteDefinitionSnapshot> = current?.routes ?: emptyList()

    /**
     * 判断目标版本是否比本地新, 用于过滤乱序与重复通知
     *
     * @param version 通知或信封携带的快照版本号
     * @return 严格大于本地版本时为 `true`, 未加载过时任何正数版本均为 `true`
     */
    fun isNewer(version: Long): Boolean = version > (current?.version ?: 0L)

    /**
     * 从 Redis 读取最新快照并按需采纳
     *
     * 路由 Key 不存在 (Admin 从未发布) 或信封损坏时保留旧快照并返回 false;
     * 成功采纳新版本返回 true, 由调用方触发 Spring Cloud Gateway 路由刷新
     *
     * @return 采纳了更新的快照时为 `true`, 读取失败、Key 缺失、信封损坏或版本不比本地新时为 `false`
     */
    @Synchronized
    fun refresh(): Boolean {
        val environment = properties.environment
        val text = try {
            aspenRedisOperations.getValue(GatewayRouteContract.routesKey(environment))
        } catch (e: Exception) {
            log.warn("路由快照 Key 读取失败, 保留本地快照 version={}", version, e)
            return false
        }
        if (text.isNullOrBlank()) {
            log.warn("路由快照 Key 尚未发布, 保留本地快照 version={}", version)
            return false
        }
        val envelope = parseEnvelope(text) ?: return false
        if (!isNewer(envelope.version)) {
            return false
        }
        current = envelope
        log.info("路由快照已采纳, version={}, routes={}", envelope.version, envelope.routes.size)
        return true
    }

    /**
     * 解析信封文本, 单条路由损坏跳过, 信封级损坏返回 null 保留旧快照
     *
     * @param text Redis 路由 Key 中的 RouteCatalogSnapshot JSON 文本
     * @return 解析成功的路由信封, 信封级损坏或缺少必要字段时返回 `null`
     */
    private fun parseEnvelope(text: String): RouteCatalogSnapshot? =
        try {
            val root = objectMapper.readTree(text)
            val version = root.get("version")?.asLong() ?: error("信封缺少 version")
            val publishedAt = root.get("publishedAt")?.asString() ?: error("信封缺少 publishedAt")
            val routesNode = root.get("routes") ?: error("信封缺少 routes")
            require(routesNode.isArray) { "信封 routes 必须为数组" }
            val routes = routesNode.mapNotNull { node ->
                try {
                    objectMapper.treeToValue(node, RouteDefinitionSnapshot::class.java)
                } catch (e: Exception) {
                    log.warn("快照内单条路由结构非法, 已跳过", e)
                    null
                }
            }
            RouteCatalogSnapshot(version = version, publishedAt = publishedAt, routes = routes)
        } catch (e: Exception) {
            log.warn("路由快照信封解析失败, 保留本地快照 version={}", version, e)
            null
        }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteSnapshotStore::class.java)
    }
}

package com.zax.aspen.common.security.consume

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.contract.AuthClientContract
import com.zax.aspen.common.security.snapshot.AuthClientCatalogSnapshot
import com.zax.aspen.common.security.snapshot.AuthClientSnapshot
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

/**
 * 从 Redis 加载并持有认证客户端配置快照的唯一入口
 *
 * 只在启动与收到刷新通知时读一次 Redis, 运行期登录策略判定全部走内存快照,
 * Redis 故障不影响已加载配置; 解析采用两段式: 信封级损坏保留旧快照并告警,
 * 单个端损坏跳过并告警, 不阻塞整体刷新; 版本比对由调用方先行完成,
 * 本类只负责加载与持有
 *
 * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
 * @param objectMapper Jackson 3 mapper, 用于信封与单端快照的反序列化
 * @param properties 安全模块配置, environment 决定读取的 Redis Key
 */
class ClientConfigSnapshotStore(
    private val aspenRedisOperations: AspenRedisOperations,
    private val objectMapper: ObjectMapper,
    private val properties: AspenSecurityProperties,
) {
    @Volatile
    private var current: AuthClientCatalogSnapshot? = null

    /** 当前持有的快照版本, 未加载过为 null */
    val version: Long?
        get() = current?.version

    /**
     * 当前持有的全部端快照, 未加载过为空列表
     *
     * @return 内存快照中的全部端定义, 未加载过或快照为空时返回空列表
     */
    fun currentSnapshots(): List<AuthClientSnapshot> = current?.clients ?: emptyList()

    /**
     * 按端编码查找快照, 认证引擎登录时定位端策略的入口
     *
     * @param clientCode 端编码, 如 aspen-admin-web
     * @return 对应端快照, 未加载、不存在或该端行损坏时返回 `null`
     */
    fun findByCode(clientCode: String): AuthClientSnapshot? =
        current?.clients?.firstOrNull { it.clientCode == clientCode }

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
     * 配置 Key 不存在 (Admin 从未发布) 或信封损坏时保留旧快照并返回 false;
     * 成功采纳新版本返回 true, 由调用方触发认证引擎的内存切换
     *
     * @return 采纳了更新的快照时为 `true`, 读取失败、Key 缺失、信封损坏或版本不比本地新时为 `false`
     */
    @Synchronized
    fun refresh(): Boolean {
        val environment = properties.environment
        val text = try {
            aspenRedisOperations.getValue(AuthClientContract.clientsKey(environment))
        } catch (e: Exception) {
            log.warn("客户端配置 Key 读取失败, 保留本地快照 version={}", version, e)
            return false
        }
        if (text.isNullOrBlank()) {
            log.warn("客户端配置 Key 尚未发布, 保留本地快照 version={}", version)
            return false
        }
        val envelope = parseEnvelope(text) ?: return false
        if (!isNewer(envelope.version)) {
            return false
        }
        current = envelope
        log.info("客户端配置快照已采纳, version={}, clients={}", envelope.version, envelope.clients.size)
        return true
    }

    /**
     * 解析信封文本, 单端损坏跳过, 信封级损坏返回 null 保留旧快照
     *
     * @param text Redis 配置 Key 中的 AuthClientCatalogSnapshot JSON 文本
     * @return 解析成功的配置信封, 信封级损坏或缺少必要字段时返回 `null`
     */
    private fun parseEnvelope(text: String): AuthClientCatalogSnapshot? =
        try {
            val root = objectMapper.readTree(text)
            val version = root.get("version")?.asLong() ?: error("信封缺少 version")
            val publishedAt = root.get("publishedAt")?.asString() ?: error("信封缺少 publishedAt")
            val clientsNode = root.get("clients") ?: error("信封缺少 clients")
            require(clientsNode.isArray) { "信封 clients 必须为数组" }
            val clients = clientsNode.mapNotNull { node ->
                try {
                    objectMapper.treeToValue(node, AuthClientSnapshot::class.java)
                } catch (e: Exception) {
                    log.warn("快照内单个端结构非法, 已跳过", e)
                    null
                }
            }
            AuthClientCatalogSnapshot(version = version, publishedAt = publishedAt, clients = clients)
        } catch (e: Exception) {
            log.warn("客户端配置信封解析失败, 保留本地快照 version={}", version, e)
            null
        }

    private companion object {
        private val log = LoggerFactory.getLogger(ClientConfigSnapshotStore::class.java)
    }
}

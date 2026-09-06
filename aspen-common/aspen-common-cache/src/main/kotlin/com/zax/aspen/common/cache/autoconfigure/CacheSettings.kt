package com.zax.aspen.common.cache.autoconfigure

import com.zax.aspen.common.cache.key.CacheKey
import com.zax.aspen.common.cache.key.CacheKeyBuilder
import java.time.Duration

/** 表示完成启动校验后的单个 Cache 定义 */
data class CacheDefinition(
    /** Cache 所属的服务内业务组 */
    val group: String,
    /** Cache 所属的业务领域 */
    val domain: String,
    /** Cache 中每个条目的存活时间 */
    val ttl: Duration,
)

/** 保存所有缓存适配器共享且不可变的启动期校验结果 */
class CacheSettings private constructor(
    /** 是否允许创建配置中未声明的 Cache */
    val allowUndeclaredCaches: Boolean,
    /** 未声明 Cache 使用的默认 TTL */
    val defaultTtl: Duration?,
    /** 单个缓存值允许的最大字节数 */
    val maxEntryBytes: Long,
    /** 按稳定 Cache 名称索引的定义 */
    val definitions: Map<String, CacheDefinition>,
    /** 使用统一部署命名空间的 Redis Key 构建器 */
    val keyBuilder: CacheKeyBuilder,
) {
    /**
     * 获取 Cache 定义并校验显式 Key 的业务命名空间
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 可选的业务缓存 Key, 命中声明定义时命名空间必须一致, 未声明 Cache 时必须提供
     * @return 命中声明的定义, 或由 Key 推导并套用默认 TTL 的未声明定义
     */
    fun definition(cacheName: String, key: CacheKey? = null): CacheDefinition {
        definitions[cacheName]?.let { definition ->
            if (key != null) {
                require(key.group == definition.group && key.domain == definition.domain) {
                    "Cache Key 的 group/domain 与定义 '$cacheName' 不匹配"
                }
            }
            return definition
        }

        require(allowUndeclaredCaches) { "Cache '$cacheName' 未在 aspen.cache.definitions 中声明" }
        requireNotNull(key) { "未声明的 Cache 必须提供明确的 CacheKey" }
        return CacheDefinition(key.group, key.domain, requireNotNull(defaultTtl))
    }

    /** 负责创建和校验不可变缓存设置 */
    companion object {
        /**
         * 从外部配置和应用名称构建不可变缓存设置
         *
         * @param properties 绑定 aspen.cache 前缀的外部配置
         * @param applicationName spring.application.name 的取值, 未显式配置 service 时作为服务标识, 可为 `null`
         * @return 通过全部启动期校验的不可变缓存设置
         */
        fun from(properties: AspenCacheProperties, applicationName: String?): CacheSettings {
            val service = properties.service.ifBlank { applicationName.orEmpty() }
            val keyBuilder = CacheKeyBuilder(
                properties.prefix,
                properties.environment,
                service,
                properties.allowedGroups,
            )
            val maxEntryBytes = properties.maxEntrySize.toBytes()
            require(maxEntryBytes >= 1) { "aspen.cache.max-entry-size 必须为正数" }

            val definitions = properties.definitions.mapValues { (cacheName, configured) ->
                requireValidCacheName(cacheName)
                val ttl = requireNotNull(configured.ttl) {
                    "必须配置 aspen.cache.definitions.$cacheName.ttl"
                }
                require(!ttl.isZero && !ttl.isNegative) {
                    "aspen.cache.definitions.$cacheName.ttl 必须为正数"
                }
                CacheDefinition(configured.group, configured.domain, ttl).also {
                    // Cache 定义与直接 Redis Key 复用相同的严格分段规则
                    keyBuilder.prefix(it.group, it.domain)
                }
            }

            // 相同 group 和 domain 会生成相同前缀, 必须在启动阶段拒绝
            val duplicateNamespaces = definitions.entries
                .groupBy { it.value.group to it.value.domain }
                .filterValues { it.size > 1 }
            require(duplicateNamespaces.isEmpty()) {
                "多个 Cache 定义不能共享相同的 group/domain 命名空间: ${duplicateNamespaces.keys}"
            }

            if (properties.allowUndeclaredCaches) {
                val defaultTtl = requireNotNull(properties.defaultTtl) {
                    "允许未声明 Cache 时必须配置 aspen.cache.default-ttl"
                }
                require(!defaultTtl.isZero && !defaultTtl.isNegative) {
                    "aspen.cache.default-ttl 必须为正数"
                }
            }

            return CacheSettings(
                allowUndeclaredCaches = properties.allowUndeclaredCaches,
                defaultTtl = properties.defaultTtl,
                maxEntryBytes = maxEntryBytes,
                definitions = definitions.toMap(),
                keyBuilder = keyBuilder,
            )
        }

        /**
         * 校验 Cache 名称可以安全用于配置索引和 Redis 前缀
         *
         * @param cacheName 待校验的稳定 Cache 名称
         */
        private fun requireValidCacheName(cacheName: String) {
            require(CACHE_NAME_PATTERN.matches(cacheName)) {
                "Cache 名称 '$cacheName' 必须以 ASCII 字母或数字开头, 且只能包含字母、数字、'.'、'_' 或 '-'"
            }
        }

        /** Cache 名称允许使用的字符和长度规则 */
        private val CACHE_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}

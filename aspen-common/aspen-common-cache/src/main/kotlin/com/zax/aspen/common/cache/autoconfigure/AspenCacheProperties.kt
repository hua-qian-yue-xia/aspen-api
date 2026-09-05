package com.zax.aspen.common.cache.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/** 绑定 Aspen Redis 缓存公共配置 */
@ConfigurationProperties("aspen.cache")
class AspenCacheProperties {
    /** 是否启用 Aspen 缓存自动配置 */
    var enabled: Boolean = true

    /** 项目级 Redis Key 前缀 */
    var prefix: String = "aspen"

    /** 用于隔离 Redis Key 的部署环境标识 */
    var environment: String = "local"

    /** 当前服务标识, 为空时使用 spring.application.name */
    var service: String = ""

    /** 当前服务允许写入 Redis Key 的业务组白名单, 空集合表示不限制 */
    var allowedGroups: MutableSet<String> = linkedSetOf()

    /** 是否允许运行时创建未声明 Cache */
    var allowUndeclaredCaches: Boolean = false

    /** 未声明 Cache 使用的默认 TTL */
    var defaultTtl: Duration? = null

    /** 单个缓存值序列化后的最大字节数 */
    var maxEntrySize: DataSize = DataSize.ofMegabytes(1)

    /** 以稳定 Cache 名称为 Key 的显式定义 */
    var definitions: MutableMap<String, Definition> = linkedMapOf()

    /** 定义单个 Cache 的业务命名空间和 TTL */
    class Definition {
        /** Cache 所属的服务内业务组 */
        var group: String = ""

        /** Cache 所属的业务领域 */
        var domain: String = ""

        /** Cache 中每个条目的存活时间 */
        var ttl: Duration? = null
    }
}

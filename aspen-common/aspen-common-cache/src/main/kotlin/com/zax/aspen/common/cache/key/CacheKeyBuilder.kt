package com.zax.aspen.common.cache.key

/** 构建完整 Redis Key, 防止业务代码遗漏部署命名空间 */
class CacheKeyBuilder(
    /** 项目级 Redis Key 前缀 */
    prefix: String,
    /** 部署环境标识 */
    environment: String,
    /** 当前微服务标识 */
    service: String,
    /** 当前服务允许使用的业务组, 空集合表示不限制 */
    allowedGroups: Set<String> = emptySet(),
) {
    /** 经过校验的固定部署命名空间 */
    private val namespace = listOf(
        CacheKeyRules.requireSegment(prefix, "prefix"),
        CacheKeyRules.requireSegment(environment, "environment"),
        CacheKeyRules.requireSegment(service, "service"),
    ).joinToString(SEPARATOR)

    /** 经过校验且不受调用方后续修改影响的业务组白名单 */
    private val allowedGroups = allowedGroups
        .map { CacheKeyRules.requireSegment(it, "allowedGroups") }
        .toSet()

    /**
     * 将业务缓存 Key 转换为完整 Redis Key
     *
     * @param key 不含部署命名空间的业务缓存 Key
     * @return 拼接部署命名空间与全部业务段后的完整 Redis Key
     */
    fun build(key: CacheKey): String = buildString {
        requireAllowedGroup(key.group)
        append(namespace)
        append(SEPARATOR)
        append(key.group)
        append(SEPARATOR)
        append(key.domain)
        key.identifiers.forEach { identifier ->
            append(SEPARATOR)
            append(identifier)
        }
    }

    /**
     * 为 Spring Cache 构建指定业务组和领域的 Key 前缀
     *
     * @param group 目标 Cache 所属的服务内业务组
     * @param domain 目标 Cache 所属的业务领域
     * @return 以分隔符结尾的完整 Redis Key 前缀
     */
    fun prefix(group: String, domain: String): String = buildString {
        requireAllowedGroup(group)
        append(namespace)
        append(SEPARATOR)
        append(CacheKeyRules.requireSegment(group, "group"))
        append(SEPARATOR)
        append(CacheKeyRules.requireSegment(domain, "domain"))
        append(SEPARATOR)
    }

    /**
     * 在配置白名单存在时拒绝当前服务未声明的业务组
     *
     * @param group 待校验的服务内业务组
     */
    private fun requireAllowedGroup(group: String) {
        if (allowedGroups.isNotEmpty()) {
            require(group in allowedGroups) {
                "group '$group' 未在 aspen.cache.allowed-groups 中声明"
            }
        }
    }

    /** 保存 Redis Key 的公共格式常量 */
    companion object {
        /** Redis Key 各段之间使用的分隔符 */
        private const val SEPARATOR = ":"
    }
}

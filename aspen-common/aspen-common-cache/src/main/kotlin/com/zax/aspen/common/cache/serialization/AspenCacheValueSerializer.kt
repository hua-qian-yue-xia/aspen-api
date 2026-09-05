package com.zax.aspen.common.cache.serialization

import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.SerializationException
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule
import java.util.regex.Pattern

/** 使用固定反序列化白名单并限制缓存值大小的 JSON 序列化器 */
class AspenCacheValueSerializer(
    /** 单个缓存值序列化后允许的最大字节数 */
    private val maxEntryBytes: Long,
) : RedisSerializer<Any> {
    /** 使用独立配置且不影响 Web ObjectMapper 的底层序列化器 */
    private val delegate: GenericJacksonJsonRedisSerializer = GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.zax.aspen.")
                .allowIfSubType("java.time.")
                .allowIfSubType("kotlin.collections.")
                .allowIfSubType(SAFE_JAVA_UTIL_TYPES)
                .allowIfSubTypeIsArray()
                .build(),
        )
        .customize { builder -> builder.addModule(KotlinModule.Builder().build()) }
        .build()

    // 缓存值大小上限必须在创建序列化器时完成校验
    init {
        require(maxEntryBytes >= 1) { "maxEntryBytes 必须为正数" }
    }

    /** 将缓存值序列化为 JSON 并在写入 Redis 前校验大小 */
    override fun serialize(value: Any?): ByteArray {
        val bytes = delegate.serialize(value)
        if (bytes.size > maxEntryBytes) {
            throw SerializationException(
                "Cache 值大小为 ${bytes.size} 字节, 超过配置上限 $maxEntryBytes 字节",
            )
        }
        return bytes
    }

    /** 在固定类型白名单约束下反序列化缓存值 */
    override fun deserialize(bytes: ByteArray?): Any? = delegate.deserialize(bytes)

    /** 保存固定且不可通过外部配置扩大的类型白名单 */
    companion object {
        /** 允许反序列化的安全 Java 集合和值类型 */
        private val SAFE_JAVA_UTIL_TYPES: Pattern = Pattern.compile(
            "java\\.util\\.(?:ArrayList|LinkedList|HashMap|LinkedHashMap|HashSet|LinkedHashSet|" +
                "TreeMap|TreeSet|UUID|Collections\\${'$'}.*|ImmutableCollections\\${'$'}.*|" +
                "Arrays\\${'$'}ArrayList)",
        )
    }
}

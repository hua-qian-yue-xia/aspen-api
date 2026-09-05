package com.zax.aspen.common.cache.serialization

import example.cache.UntrustedPayload
import org.springframework.data.redis.serializer.SerializationException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 验证缓存 JSON 序列化的兼容性, 大小限制和类型安全边界 */
class AspenCacheValueSerializerTest {
    /** 验证 Aspen Kotlin 类型和时间类型可以通过 JSON 往返转换 */
    @Test
    fun `round trips Aspen Kotlin values and time types as JSON`() {
        val serializer = AspenCacheValueSerializer(16_384)
        val value = CachedUser(42, "Ada", Instant.parse("2026-09-02T06:30:00Z"), listOf("admin"))

        val bytes = requireNotNull(serializer.serialize(value))
        val restored = serializer.deserialize(bytes)

        assertEquals(value, restored)
        assertFalse(bytes.take(2).toByteArray().contentEquals(byteArrayOf(0xAC.toByte(), 0xED.toByte())))
    }

    /** 验证集合使用 JSON 而不是 Java 原生序列化格式 */
    @Test
    fun `round trips collections without Java serialization`() {
        val serializer = AspenCacheValueSerializer(16_384)
        val value = listOf(CachedUser(1, "A", Instant.EPOCH, emptyList()))

        val bytes = requireNotNull(serializer.serialize(value))

        assertEquals(value, serializer.deserialize(bytes))
        assertContentEquals("[".encodeToByteArray(), bytes.copyOfRange(0, 1))
    }

    /** 验证序列化结果超过配置字节数时拒绝写入 */
    @Test
    fun `rejects values above the configured byte limit`() {
        val serializer = AspenCacheValueSerializer(32)

        assertFailsWith<SerializationException> {
            serializer.serialize(CachedUser(42, "x".repeat(256), Instant.EPOCH, emptyList()))
        }
    }

    /** 验证固定白名单以外的类型不能被反序列化 */
    @Test
    fun `rejects types outside the fixed deserialization allow list`() {
        val serializer = AspenCacheValueSerializer(16_384)
        val bytes = serializer.serialize(UntrustedPayload("do-not-load"))

        assertFailsWith<SerializationException> { serializer.deserialize(bytes) }
    }

    /** 提供包含 Kotlin 集合和时间类型的可信缓存测试值 */
    data class CachedUser(
        /** 唯一标识测试用户 */
        val id: Long,
        /** 表示测试用户显示名称 */
        val name: String,
        /** 表示缓存内容最后更新时间 */
        val updatedAt: Instant,
        /** 表示测试用户拥有的角色集合 */
        val roles: List<String>,
    )
}

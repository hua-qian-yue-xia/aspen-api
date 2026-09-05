package com.zax.aspen.common.cache.support

import com.zax.aspen.common.cache.autoconfigure.AspenCacheProperties
import com.zax.aspen.common.cache.autoconfigure.CacheSettings
import com.zax.aspen.common.cache.key.CacheKey
import com.zax.aspen.common.core.error.CommonErrorCode
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 验证显式缓存操作不会吞掉 Redis 基础设施故障 */
class DefaultAspenCacheOperationsTest {
    /** 验证 Redis 读取失败会转换为统一异常且不会伪造缓存命中 */
    @Test
    fun `wraps Redis failures without reporting a cache hit`() {
        val redisTemplate = redisTemplate()
        val valueOperations = valueOperations()
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get("aspen:test:admin:upm:user:42"))
            .thenThrow(RedisConnectionFailureException("redis unavailable"))
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())

        val exception = assertFailsWith<CacheAccessException> {
            operations.get("upm-user", CacheKey("upm", "user", "42"), String::class.java)
        }

        assertEquals(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception.errorCode)
        assertEquals("缓存读取失败", exception.detail)
    }

    /** 验证 Redis 写入失败会向调用方报告而不是伪造成功 */
    @Test
    fun `wraps Redis failures without reporting a successful write`() {
        val redisTemplate = redisTemplate()
        val valueOperations = valueOperations()
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        doThrow(RedisConnectionFailureException("redis unavailable"))
            .`when`(valueOperations)
            .set("aspen:test:admin:upm:user:42", "cached-user", Duration.ofMinutes(30))
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())

        val exception = assertFailsWith<CacheAccessException> {
            operations.put("upm-user", CacheKey("upm", "user", "42"), "cached-user")
        }

        assertEquals(CommonErrorCode.DEPENDENCY_UNAVAILABLE, exception.errorCode)
        assertEquals("缓存写入失败", exception.detail)
    }

    /** 验证缓存类型不匹配时不会向调用方暴露 Cache 名称或 Java 类型 */
    @Test
    fun `hides internal cache type details from callers`() {
        val redisTemplate = redisTemplate()
        val valueOperations = valueOperations()
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get("aspen:test:admin:upm:user:42")).thenReturn(42)
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())

        val exception = assertFailsWith<CacheAccessException> {
            operations.get("upm-user", CacheKey("upm", "user", "42"), String::class.java)
        }

        assertEquals("缓存数据类型不符合预期", exception.detail)
        assertIs<ClassCastException>(exception.cause)
    }

    /** 验证条件写入始终使用声明的 TTL 并保留 Redis 原子返回语义 */
    @Test
    fun `supports atomic conditional writes with declared ttl`() {
        val redisTemplate = redisTemplate()
        val valueOperations = valueOperations()
        val redisKey = "aspen:test:admin:upm:user:42"
        val ttl = Duration.ofMinutes(30)
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.setIfAbsent(redisKey, "created", ttl)).thenReturn(true)
        `when`(valueOperations.setIfPresent(redisKey, "replaced", ttl)).thenReturn(false)
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())
        val key = CacheKey("upm", "user", "42")

        assertTrue(operations.putIfAbsent("upm-user", key, "created"))
        assertFalse(operations.putIfPresent("upm-user", key, "replaced"))
        verify(valueOperations).setIfAbsent(redisKey, "created", ttl)
        verify(valueOperations).setIfPresent(redisKey, "replaced", ttl)
    }

    /** 验证读取并删除使用单条原子命令且仍执行类型检查 */
    @Test
    fun `atomically gets and evicts a typed cache value`() {
        val redisTemplate = redisTemplate()
        val valueOperations = valueOperations()
        val redisKey = "aspen:test:admin:upm:user:42"
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.getAndDelete(redisKey)).thenReturn("cached-user")
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())

        val value = operations.getAndEvict(
            "upm-user",
            CacheKey("upm", "user", "42"),
            String::class.java,
        )

        assertEquals("cached-user", value)
    }

    /** 验证存在性检查使用完整命名空间且返回明确布尔值 */
    @Test
    fun `checks namespaced cache key existence`() {
        val redisTemplate = redisTemplate()
        `when`(redisTemplate.hasKey("aspen:test:admin:upm:user:42")).thenReturn(true)
        val operations = DefaultAspenCacheOperations(redisTemplate, settings())

        assertTrue(operations.contains("upm-user", CacheKey("upm", "user", "42")))
    }

    /** 创建使用受控泛型签名的 RedisTemplate 测试替身 */
    @Suppress("UNCHECKED_CAST")
    private fun redisTemplate(): RedisTemplate<String, Any> =
        mock(RedisTemplate::class.java) as RedisTemplate<String, Any>

    /** 创建使用受控泛型签名的 ValueOperations 测试替身 */
    @Suppress("UNCHECKED_CAST")
    private fun valueOperations(): ValueOperations<String, Any> =
        mock(ValueOperations::class.java) as ValueOperations<String, Any>

    /** 创建与测试 Key 命名空间一致的合法缓存设置 */
    private fun settings(): CacheSettings {
        val properties = AspenCacheProperties().apply {
            environment = "test"
            service = "admin"
            definitions["upm-user"] = AspenCacheProperties.Definition().also {
                it.group = "upm"
                it.domain = "user"
                it.ttl = Duration.ofMinutes(30)
            }
        }
        return CacheSettings.from(properties, null)
    }
}

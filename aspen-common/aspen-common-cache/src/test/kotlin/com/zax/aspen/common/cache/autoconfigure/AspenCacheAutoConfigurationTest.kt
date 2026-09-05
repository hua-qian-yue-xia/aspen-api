package com.zax.aspen.common.cache.autoconfigure

import com.zax.aspen.common.cache.support.AspenCacheOperations
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证缓存公共模块的自动配置条件和严格缓存策略 */
class AspenCacheAutoConfigurationTest {
    /** 创建同时加载 Boot Redis 和 Aspen 缓存配置的隔离上下文 */
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration::class.java,
                AspenCacheAutoConfiguration::class.java,
                CacheAutoConfiguration::class.java,
            ),
        )
        .withBean(RedisConnectionFactory::class.java, { mock(RedisConnectionFactory::class.java) })
        .withPropertyValues(
            "spring.application.name=aspen-admin-biz",
            "aspen.cache.environment=test",
            "aspen.cache.definitions.upm-user.group=upm",
            "aspen.cache.definitions.upm-user.domain=user",
            "aspen.cache.definitions.upm-user.ttl=30m",
        )

    /** 验证自动配置无需建立 Redis 网络连接即可完成 Bean 装配 */
    @Test
    fun `creates strict cache infrastructure without connecting to Redis`() {
        contextRunner.run { context ->
            assertNull(context.startupFailure)
            assertTrue(context.containsBean("aspenRedisTemplate"))
            assertNotNull(context.getBean(AspenCacheOperations::class.java))
            val cacheManager = context.getBean(CacheManager::class.java)
            assertEquals(setOf("aspenRedisCacheManager"), context.getBeansOfType(CacheManager::class.java).keys)
            assertTrue(context.getBeansOfType(RedisTemplate::class.java).containsKey("redisTemplate"))
            assertNotNull(cacheManager.getCache("upm-user"))
            assertNull(cacheManager.getCache("undeclared"))
        }
    }

    /** 验证缺少 RedisConnectionFactory 时自动配置不会加载 */
    @Test
    fun `does not load without a connection factory`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AspenCacheAutoConfiguration::class.java))
            .run { context ->
                assertFalse(context.containsBean("aspenRedisTemplate"))
            }
    }

    /** 验证显式关闭缓存时自动配置不会加载 */
    @Test
    fun `does not load when explicitly disabled`() {
        contextRunner
            .withPropertyValues("aspen.cache.enabled=false")
            .run { context ->
                assertFalse(context.containsBean("aspenRedisTemplate"))
            }
    }

    /** 验证缺少 TTL 的缓存定义会阻止应用启动 */
    @Test
    fun `fails fast for incomplete cache definitions`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AspenCacheAutoConfiguration::class.java))
            .withBean(RedisConnectionFactory::class.java, { mock(RedisConnectionFactory::class.java) })
            .withPropertyValues(
                "spring.application.name=aspen-admin-biz",
                "aspen.cache.definitions.upm-user.group=upm",
                "aspen.cache.definitions.upm-user.domain=user",
            )
            .run { context ->
                assertNotNull(context.startupFailure)
            }
    }

    /** 验证用户提供的缓存基础 Bean 会阻止同类型默认 Bean 创建 */
    @Test
    fun `backs off for user cache beans`() {
        contextRunner
            .withUserConfiguration(CustomCacheBeansConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertEquals(
                    setOf("customCacheManager"),
                    context.getBeansOfType(CacheManager::class.java).keys,
                )
                assertEquals(
                    setOf("customCacheOperations"),
                    context.getBeansOfType(AspenCacheOperations::class.java).keys,
                )
                assertTrue(context.containsBean("aspenRedisTemplate"))
            }
    }

    /** 提供覆盖默认缓存管理器, RedisTemplate 和缓存操作的用户配置 */
    @Configuration(proxyBeanMethods = false)
    class CustomCacheBeansConfiguration {
        /** 注册业务服务自行定义的 CacheManager */
        @Bean
        fun customCacheManager(): CacheManager = mock(CacheManager::class.java)

        /** 使用约定名称注册业务服务自行定义的 RedisTemplate */
        @Bean("aspenRedisTemplate")
        @Suppress("UNCHECKED_CAST")
        fun customRedisTemplate(): RedisTemplate<String, Any> =
            mock(RedisTemplate::class.java) as RedisTemplate<String, Any>

        /** 注册业务服务自行定义的显式缓存操作 */
        @Bean
        fun customCacheOperations(): AspenCacheOperations = mock(AspenCacheOperations::class.java)
    }
}

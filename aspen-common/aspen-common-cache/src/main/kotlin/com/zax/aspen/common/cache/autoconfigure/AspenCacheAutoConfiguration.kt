package com.zax.aspen.common.cache.autoconfigure

import com.zax.aspen.common.cache.key.CacheKeyBuilder
import com.zax.aspen.common.cache.serialization.AspenCacheValueSerializer
import com.zax.aspen.common.cache.support.AspenCacheOperations
import com.zax.aspen.common.cache.support.DefaultAspenCacheOperations
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import org.springframework.data.redis.serializer.StringRedisSerializer

/** 在 Redis 连接可用时注册严格且可覆盖的 Aspen 缓存基础设施 */
@AutoConfiguration(
    after = [DataRedisAutoConfiguration::class],
    before = [CacheAutoConfiguration::class],
)
@ConditionalOnClass(RedisTemplate::class, RedisCacheManager::class)
@ConditionalOnBean(RedisConnectionFactory::class)
@ConditionalOnProperty(prefix = "aspen.cache", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AspenCacheProperties::class)
class AspenCacheAutoConfiguration {
    /** 合并外部配置和应用名称并执行启动期校验 */
    @Bean("aspenCacheSettings")
    @ConditionalOnMissingBean(CacheSettings::class)
    fun aspenCacheSettings(properties: AspenCacheProperties, environment: Environment): CacheSettings =
        CacheSettings.from(properties, environment.getProperty("spring.application.name"))

    /** 暴露所有缓存入口共用的 Redis Key 构建器 */
    @Bean("aspenCacheKeyBuilder")
    @ConditionalOnMissingBean(CacheKeyBuilder::class)
    fun aspenCacheKeyBuilder(settings: CacheSettings): CacheKeyBuilder = settings.keyBuilder

    /** 创建带大小限制和类型白名单的 JSON 序列化器 */
    @Bean("aspenCacheValueSerializer")
    @ConditionalOnMissingBean(name = ["aspenCacheValueSerializer"])
    fun aspenCacheValueSerializer(settings: CacheSettings): AspenCacheValueSerializer =
        AspenCacheValueSerializer(settings.maxEntryBytes)

    /** 创建使用字符串 Key 和受控 JSON 值的专用 RedisTemplate */
    @Bean("aspenRedisTemplate")
    @ConditionalOnMissingBean(name = ["aspenRedisTemplate"])
    fun aspenRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        serializer: AspenCacheValueSerializer,
    ): RedisTemplate<String, Any> = RedisTemplate<String, Any>().apply {
        setConnectionFactory(connectionFactory)
        keySerializer = StringRedisSerializer()
        valueSerializer = serializer
        hashKeySerializer = StringRedisSerializer()
        hashValueSerializer = serializer
        defaultSerializer = serializer
        afterPropertiesSet()
    }

    /** 根据显式 Cache 定义创建 RedisCacheManager */
    @Bean
    @ConditionalOnMissingBean(CacheManager::class)
    fun aspenRedisCacheManager(
        connectionFactory: RedisConnectionFactory,
        settings: CacheSettings,
        serializer: AspenCacheValueSerializer,
    ): RedisCacheManager {
        val keyPair = SerializationPair.fromSerializer(StringRedisSerializer())
        val valuePair = SerializationPair.fromSerializer(serializer)
        val baseConfiguration = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(keyPair)
            .serializeValuesWith(valuePair)

        // 每个已声明 Cache 使用独立且显式的 TTL 和业务命名空间
        val configurations = settings.definitions.mapValues { (_, definition) ->
            baseConfiguration
                .entryTtl(definition.ttl)
                .computePrefixWith { settings.keyBuilder.prefix(definition.group, definition.domain) }
        }

        // 未声明 Cache 只能在显式放开时进入隔离的 unclassified 命名空间
        val defaultConfiguration = if (settings.allowUndeclaredCaches) {
            baseConfiguration
                .entryTtl(requireNotNull(settings.defaultTtl))
                .computePrefixWith { cacheName ->
                    settings.keyBuilder.prefix("unclassified", cacheName)
                }
        } else {
            baseConfiguration
        }

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfiguration)
            .allowCreateOnMissingCache(settings.allowUndeclaredCaches)
            .withInitialCacheConfigurations(configurations)
            .build()
    }

    /** 创建会统一包装 Redis 失败的显式缓存操作接口 */
    @Bean
    @ConditionalOnMissingBean(AspenCacheOperations::class)
    fun aspenCacheOperations(
        @Qualifier("aspenRedisTemplate")
        aspenRedisTemplate: RedisTemplate<String, Any>,
        settings: CacheSettings,
    ): AspenCacheOperations = DefaultAspenCacheOperations(aspenRedisTemplate, settings)
}

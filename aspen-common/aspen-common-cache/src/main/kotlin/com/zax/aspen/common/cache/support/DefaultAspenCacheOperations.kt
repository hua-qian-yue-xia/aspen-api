package com.zax.aspen.common.cache.support

import com.zax.aspen.common.cache.autoconfigure.CacheSettings
import com.zax.aspen.common.cache.key.CacheKey
import org.springframework.data.redis.core.RedisTemplate

/** 使用统一 Key, TTL 和异常语义实现显式缓存操作 */
internal class DefaultAspenCacheOperations(
    /** 只使用 Aspen 受控序列化器的 Redis 模板 */
    private val redisTemplate: RedisTemplate<String, Any>,
    /** 完成启动校验的缓存定义和命名空间 */
    private val settings: CacheSettings,
) : AspenCacheOperations {
    /** 读取缓存并验证返回值符合调用方声明的类型 */
    override fun <T : Any> get(cacheName: String, key: CacheKey, type: Class<T>): T? =
        read(cacheName, key, type, "缓存读取失败") { redisKey ->
            redisTemplate.opsForValue().get(redisKey)
        }

    /** 原子读取并删除缓存值, 避免一次性数据被并发重复消费 */
    override fun <T : Any> getAndEvict(cacheName: String, key: CacheKey, type: Class<T>): T? =
        read(cacheName, key, type, "缓存读取并删除失败") { redisKey ->
            redisTemplate.opsForValue().getAndDelete(redisKey)
        }

    /** 使用配置中声明的 TTL 写入缓存 */
    override fun put(cacheName: String, key: CacheKey, value: Any) {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        execute("缓存写入失败") {
            redisTemplate.opsForValue().set(redisKey, value, definition.ttl)
        }
    }

    /** 仅在缓存不存在时原子写入并同时设置配置声明的 TTL */
    override fun putIfAbsent(cacheName: String, key: CacheKey, value: Any): Boolean {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存写入失败") {
            redisTemplate.opsForValue().setIfAbsent(redisKey, value, definition.ttl) == true
        }
    }

    /** 仅在缓存已经存在时原子覆盖并重置为配置声明的 TTL */
    override fun putIfPresent(cacheName: String, key: CacheKey, value: Any): Boolean {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存写入失败") {
            redisTemplate.opsForValue().setIfPresent(redisKey, value, definition.ttl) == true
        }
    }

    /** 检查完整命名空间下的缓存 Key 是否存在且不承诺后续状态不变 */
    override fun contains(cacheName: String, key: CacheKey): Boolean {
        settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存存在性检查失败") { redisTemplate.hasKey(redisKey) == true }
    }

    /** 删除完整命名空间下的缓存条目 */
    override fun evict(cacheName: String, key: CacheKey): Boolean {
        settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存删除失败") { redisTemplate.delete(redisKey) == true }
    }

    /** 统一执行缓存读取命令并验证反序列化结果类型 */
    private fun <T : Any> read(
        cacheName: String,
        key: CacheKey,
        type: Class<T>,
        safeDetail: String,
        loader: (String) -> Any?,
    ): T? {
        settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute(safeDetail) {
            val value = loader(redisKey) ?: return@execute null
            cast(value, type)
        }
    }

    /** 将不可信缓存值转换为调用方声明的类型且隐藏内部类型信息 */
    private fun <T : Any> cast(value: Any, type: Class<T>): T = try {
        type.cast(value)
    } catch (exception: ClassCastException) {
        throw CacheAccessException("缓存数据类型不符合预期", exception)
    }

    /** 将 Redis 和序列化运行时异常统一转换为缓存访问异常 */
    private fun <T> execute(safeDetail: String, block: () -> T): T = try {
        block()
    } catch (exception: CacheAccessException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw CacheAccessException(safeDetail, exception)
    }
}

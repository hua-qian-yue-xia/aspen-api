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
    override fun <T : Any> get(cacheName: String, key: CacheKey, type: Class<T>): T? =
        read(cacheName, key, type, "缓存读取失败") { redisKey ->
            redisTemplate.opsForValue().get(redisKey)
        }

    override fun <T : Any> getAndEvict(cacheName: String, key: CacheKey, type: Class<T>): T? =
        read(cacheName, key, type, "缓存读取并删除失败") { redisKey ->
            redisTemplate.opsForValue().getAndDelete(redisKey)
        }

    override fun put(cacheName: String, key: CacheKey, value: Any) {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        execute("缓存写入失败") {
            redisTemplate.opsForValue().set(redisKey, value, definition.ttl)
        }
    }

    override fun putIfAbsent(cacheName: String, key: CacheKey, value: Any): Boolean {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存写入失败") {
            redisTemplate.opsForValue().setIfAbsent(redisKey, value, definition.ttl) == true
        }
    }

    override fun putIfPresent(cacheName: String, key: CacheKey, value: Any): Boolean {
        val definition = settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存写入失败") {
            redisTemplate.opsForValue().setIfPresent(redisKey, value, definition.ttl) == true
        }
    }

    override fun contains(cacheName: String, key: CacheKey): Boolean {
        settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存存在性检查失败") { redisTemplate.hasKey(redisKey) == true }
    }

    override fun evict(cacheName: String, key: CacheKey): Boolean {
        settings.definition(cacheName, key)
        val redisKey = settings.keyBuilder.build(key)
        return execute("缓存删除失败") { redisTemplate.delete(redisKey) == true }
    }

    /**
     * 统一执行缓存读取命令并验证反序列化结果类型
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @param type 调用方期望的缓存值类型
     * @param safeDetail 包装失败时对外暴露的安全错误详情
     * @param loader 实际执行读取的代码块, 入参为完整 Redis Key
     * @return 读取并完成类型校验的缓存值, 未命中时返回 `null`
     */
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

    /**
     * 将不可信缓存值转换为调用方声明的类型且隐藏内部类型信息
     *
     * @param value 反序列化得到的缓存值
     * @param type 调用方声明的期望类型
     * @return 完成类型校验后的缓存值
     */
    private fun <T : Any> cast(value: Any, type: Class<T>): T = try {
        type.cast(value)
    } catch (exception: ClassCastException) {
        throw CacheAccessException("缓存数据类型不符合预期", exception)
    }

    /**
     * 将 Redis 和序列化运行时异常统一转换为缓存访问异常
     *
     * @param safeDetail 包装失败时对外暴露的安全错误详情
     * @param block 实际执行缓存命令的代码块
     * @return 代码块的执行结果
     */
    private fun <T> execute(safeDetail: String, block: () -> T): T = try {
        block()
    } catch (exception: CacheAccessException) {
        throw exception
    } catch (exception: RuntimeException) {
        throw CacheAccessException(safeDetail, exception)
    }
}

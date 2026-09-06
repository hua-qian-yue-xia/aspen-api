package com.zax.aspen.common.cache.support

import com.zax.aspen.common.cache.key.CacheKey

/** 为需要明确 TTL 和失败行为的业务链路提供显式缓存操作 */
interface AspenCacheOperations {
    /**
     * 读取并校验指定类型的缓存值
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key, 命中声明定义时命名空间必须一致
     * @param type 调用方期望的缓存值类型, 反序列化结果不符合时视为缓存访问失败
     * @return 反序列化并完成类型校验的缓存值, 未命中时返回 `null`
     * @throws CacheAccessException Redis 访问、序列化或类型校验失败时抛出
     */
    fun <T : Any> get(cacheName: String, key: CacheKey, type: Class<T>): T?

    /**
     * 原子读取并删除指定缓存值, 避免一次性数据被并发重复消费
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @param type 调用方期望的缓存值类型, 反序列化结果不符合时视为缓存访问失败
     * @return 读取并删除前的缓存值, 未命中时返回 `null`
     * @throws CacheAccessException Redis 访问、序列化或类型校验失败时抛出
     */
    fun <T : Any> getAndEvict(cacheName: String, key: CacheKey, type: Class<T>): T?

    /**
     * 使用已声明 Cache 的 TTL 写入非空缓存值
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @param value 待写入的缓存值, 不允许为 `null`
     * @throws CacheAccessException Redis 访问或序列化失败时抛出
     */
    fun put(cacheName: String, key: CacheKey, value: Any)

    /**
     * 仅在目标 Key 不存在时使用已声明 TTL 原子写入
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @param value 待写入的缓存值
     * @return Key 不存在且写入成功返回 true, Key 已存在未写入返回 false
     * @throws CacheAccessException Redis 访问或序列化失败时抛出
     */
    fun putIfAbsent(cacheName: String, key: CacheKey, value: Any): Boolean

    /**
     * 仅在目标 Key 已存在时使用已声明 TTL 原子覆盖
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @param value 待覆盖写入的缓存值
     * @return Key 已存在且覆盖成功返回 true, Key 不存在未写入返回 false
     * @throws CacheAccessException Redis 访问或序列化失败时抛出
     */
    fun putIfPresent(cacheName: String, key: CacheKey, value: Any): Boolean

    /**
     * 判断指定缓存 Key 当前是否存在, 返回值不得用于先检查再写入
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @return Key 当前存在返回 true, 不存在返回 false, 不承诺后续状态不变
     * @throws CacheAccessException Redis 访问失败时抛出
     */
    fun contains(cacheName: String, key: CacheKey): Boolean

    /**
     * 删除指定 Cache 中的业务 Key
     *
     * @param cacheName 已声明的稳定 Cache 名称
     * @param key 定位缓存条目的业务缓存 Key
     * @return 条目存在且删除成功返回 true, 条目不存在返回 false
     * @throws CacheAccessException Redis 访问失败时抛出
     */
    fun evict(cacheName: String, key: CacheKey): Boolean
}

/**
 * 使用 Kotlin 实化类型参数简化类型安全的缓存读取
 *
 * @param cacheName 已声明的稳定 Cache 名称
 * @param key 定位缓存条目的业务缓存 Key
 * @return 反序列化并完成类型校验的缓存值, 未命中时返回 `null`
 */
inline fun <reified T : Any> AspenCacheOperations.get(cacheName: String, key: CacheKey): T? =
    get(cacheName, key, T::class.java)

/**
 * 使用 Kotlin 实化类型参数简化原子读取并删除
 *
 * @param cacheName 已声明的稳定 Cache 名称
 * @param key 定位缓存条目的业务缓存 Key
 * @return 读取并删除前的缓存值, 未命中时返回 `null`
 */
inline fun <reified T : Any> AspenCacheOperations.getAndEvict(cacheName: String, key: CacheKey): T? =
    getAndEvict(cacheName, key, T::class.java)

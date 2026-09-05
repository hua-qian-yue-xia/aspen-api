package com.zax.aspen.common.cache.support

import com.zax.aspen.common.cache.key.CacheKey

/** 为需要明确 TTL 和失败行为的业务链路提供显式缓存操作 */
interface AspenCacheOperations {
    /** 读取并校验指定类型的缓存值 */
    fun <T : Any> get(cacheName: String, key: CacheKey, type: Class<T>): T?

    /** 原子读取并删除指定缓存值, 未命中时返回 null */
    fun <T : Any> getAndEvict(cacheName: String, key: CacheKey, type: Class<T>): T?

    /** 使用已声明 Cache 的 TTL 写入非空缓存值 */
    fun put(cacheName: String, key: CacheKey, value: Any)

    /** 仅在目标 Key 不存在时使用已声明 TTL 原子写入 */
    fun putIfAbsent(cacheName: String, key: CacheKey, value: Any): Boolean

    /** 仅在目标 Key 已存在时使用已声明 TTL 原子覆盖 */
    fun putIfPresent(cacheName: String, key: CacheKey, value: Any): Boolean

    /** 判断指定缓存 Key 当前是否存在, 返回值不得用于先检查再写入 */
    fun contains(cacheName: String, key: CacheKey): Boolean

    /** 删除指定 Cache 中的业务 Key */
    fun evict(cacheName: String, key: CacheKey): Boolean
}

/** 使用 Kotlin 实化类型参数简化类型安全的缓存读取 */
inline fun <reified T : Any> AspenCacheOperations.get(cacheName: String, key: CacheKey): T? =
    get(cacheName, key, T::class.java)

/** 使用 Kotlin 实化类型参数简化原子读取并删除 */
inline fun <reified T : Any> AspenCacheOperations.getAndEvict(cacheName: String, key: CacheKey): T? =
    getAndEvict(cacheName, key, T::class.java)

package com.zax.aspen.task.biz.tenant

import com.zax.aspen.common.cache.key.CacheKey

/**
 * Task 服务缓存 Key 的唯一构造入口
 *
 * 全部缓存 Key 经本工厂构造, 禁止在业务代码手拼 Key 段; Cache 声明与 TTL 随
 * 第一个消费方在 application.yaml 的 aspen.cache.definitions 登记
 */
object TaskCacheKeys {
    /** 启用租户快照的稳定 Cache 名, 与 aspen.cache.definitions 声明同名 */
    const val TENANT_SNAPSHOT_CACHE = "task-tenant-snapshot"

    /**
     * 构造启用租户快照的缓存 Key
     *
     * @return 全租户投递共享的单一快照 Key
     */
    fun enabledTenantSnapshot(): CacheKey = CacheKey("task", "tenant-snapshot", "enabled")
}

package com.zax.aspen.task.biz.tenant

import com.zax.aspen.admin.api.contract.upm.UpmTenantApi
import com.zax.aspen.admin.api.dto.upm.TenantBriefDto
import com.zax.aspen.common.cache.key.CacheKey
import com.zax.aspen.common.cache.support.AspenCacheOperations
import com.zax.aspen.common.cache.support.CacheAccessException
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.dispatch.http.TaskHttpDispatcher
import com.zax.aspen.task.biz.dispatch.http.TaskHttpRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration

/**
 * 全租户任务的启用租户清单来源
 *
 * 经 Admin 内部契约拉取启用租户并做短 TTL 缓存防抖: 缓存未命中回源, Redis 故障
 * 降级直连拉取 (缓存只是防抖介质, 权威数据在 Admin 库); 回源失败向调用方抛出
 * 异常, 由调度侧整轮跳过并记录错误, 不静默返回过期清单
 */
@Component
class TenantSourceClient(
    private val httpDispatcher: TaskHttpDispatcher,
    private val cacheOperations: AspenCacheOperations,
    private val properties: AspenTaskProperties,
    private val objectMapper: ObjectMapper,
) {
    /**
     * 查询全部启用租户, 优先读缓存
     *
     * @return 启用租户简要信息列表, 无启用租户时返回空列表
     * @throws IllegalStateException 租户来源未配置、Admin 不可达或响应不可解析时抛出
     */
    fun listEnabledTenants(): List<TenantBriefDto> {
        val baseUrl = properties.tenantSource.baseUrl.trim().removeSuffix("/")
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("租户来源未配置: aspen.task.tenant-source.base-url")
        readSnapshotFromCache()?.let { return it.tenants }

        val result = httpDispatcher.dispatch(
            TaskHttpRequest(
                method = TaskHttpMethod.GET,
                url = "$baseUrl${UpmTenantApi.PATH}/enabled",
                headers = emptyMap(),
                body = null,
                timeout = Duration.ofSeconds(10),
            ),
        )
        if (!result.success || result.body == null) {
            throw IllegalStateException(
                "拉取启用租户失败: ${result.failureKind?.code ?: "unknown"} ${result.errorMessage ?: ""}".trim(),
            )
        }
        val tenants = try {
            objectMapper.readValue<List<TenantBriefDto>>(result.body)
        } catch (e: Exception) {
            throw IllegalStateException("启用租户响应解析失败", e)
        }
        val snapshot = EnabledTenantsSnapshot(tenants)
        writeSnapshotToCache(snapshot)
        return snapshot.tenants
    }

    /**
     * 读取缓存快照, Redis 故障按未命中处理
     *
     * @return 命中的快照, 未命中或 Redis 不可用时返回 null
     */
    private fun readSnapshotFromCache(): EnabledTenantsSnapshot? =
        try {
            cacheOperations.get(
                TaskCacheKeys.TENANT_SNAPSHOT_CACHE,
                TaskCacheKeys.enabledTenantSnapshot(),
                EnabledTenantsSnapshot::class.java,
            )
        } catch (e: CacheAccessException) {
            log.debug("租户快照缓存读取失败, 降级直连 Admin 拉取", e)
            null
        }

    /**
     * 写入缓存快照, Redis 故障只记录不影响投递
     *
     * @param snapshot 待写入的快照
     */
    private fun writeSnapshotToCache(snapshot: EnabledTenantsSnapshot) {
        try {
            cacheOperations.put(TaskCacheKeys.TENANT_SNAPSHOT_CACHE, TaskCacheKeys.enabledTenantSnapshot(), snapshot)
        } catch (e: CacheAccessException) {
            log.debug("租户快照缓存写入失败, 跳过防抖", e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TenantSourceClient::class.java)
    }
}

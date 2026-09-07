package com.zax.aspen.admin.biz.messaging.redis.sys

import com.zax.aspen.admin.api.constant.GatewayRouteContract
import com.zax.aspen.admin.api.event.sys.RouteCatalogSnapshot
import com.zax.aspen.admin.api.event.sys.RouteDefinitionSnapshot
import com.zax.aspen.admin.biz.config.sys.RoutePublishProperties
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.repository.sys.SysRouteRepository
import com.zax.aspen.common.cache.support.AspenRedisOperations
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 把 sys_route 全量发布为网关路由快照
 *
 * 链路: 取号 (INCR 版本计数器) -> 构建信封 -> 一条 SET 原子替换路由 Key (无删除
 * 窗口) -> Pub/Sub 携带版本号通知各 Gateway 实例; 路由增删改经 AFTER_COMMIT 监听
 * 触发, 启动首发经 RoutePublishStartupRunner 触发; Redis 只是分发介质, 发布失败
 * 只记录错误不回滚数据库, 由下次变更或重启自愈; Redis 访问经 common-cache 的
 * AspenRedisOperations 分发原语, JSON 列由 Jimmer @Serialized 在查询时还原为
 * 类型化集合, 本类只负责信封序列化
 */
@Component
class RouteDefinitionPublisher(
    private val sysRouteRepository: SysRouteRepository,
    private val aspenRedisOperations: AspenRedisOperations,
    private val objectMapper: ObjectMapper,
    private val routePublishProperties: RoutePublishProperties,
    private val clock: Clock,
) {
    /**
     * 监听已提交的路由变更并重发布快照
     *
     * @param event 路由变更领域事件, 无载荷, 仅作为触发信号
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRouteChanged(event: SysRouteChangedEvent) {
        publishAllSafely()
    }

    /**
     * 发布快照并吞掉 Redis 故障, 用于事务提交后与启动首发等不允许中断的调用点
     */
    fun publishAllSafely() {
        try {
            publishAll()
        } catch (e: Exception) {
            log.error("路由快照发布失败, Redis 数据待下次变更或重启自愈", e)
        }
    }

    /**
     * 全量构建并原子发布路由快照, 返回发布版本
     *
     * @return 本次发布经 Redis 版本计数器 INCR 产生的版本号, 单调递增
     */
    fun publishAll(): Long {
        val environment = routePublishProperties.environment
        val routes = sysRouteRepository.findAllEnabled().mapNotNull { it.toSnapshotOrNull() }
        val version = aspenRedisOperations.increment(GatewayRouteContract.versionKey(environment))
        val envelope = RouteCatalogSnapshot(
            version = version,
            publishedAt = OffsetDateTime.now(clock).toString(),
            routes = routes,
        )
        aspenRedisOperations.setValue(
            GatewayRouteContract.routesKey(environment),
            objectMapper.writeValueAsString(envelope),
        )
        aspenRedisOperations.publish(GatewayRouteContract.refreshChannel(environment), version.toString())
        log.info("路由快照已发布, version={}, routes={}", version, routes.size)
        return version
    }

    /**
     * 把启用路由行转换为快照; 结构损坏在查询阶段整体失败, 语义非法 (如无断言) 跳过该行并告警
     *
     * @return 合法的路由发布快照, 行语义非法无法构造时返回 `null` 并记录告警
     */
    private fun SysRouteEntity.toSnapshotOrNull(): RouteDefinitionSnapshot? =
        try {
            RouteDefinitionSnapshot(
                routeCode = routeCode,
                uri = uri,
                order = sortOrder,
                predicates = predicates,
                filters = filters,
                metadata = metadata ?: emptyMap(),
            )
        } catch (e: Exception) {
            log.warn("路由行语义非法, 已跳过发布: routeId={}, routeCode={}", routeId, routeCode, e)
            null
        }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteDefinitionPublisher::class.java)
    }
}

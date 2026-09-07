package com.zax.aspen.gateway.health

import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import jakarta.annotation.Resource
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * 路由快照加载状态的健康指示
 *
 * 从未成功加载过快照时报 DOWN, 提示部署编排层网关处于「空路由」状态; 已加载后
 * 运行期不再访问 Redis, Redis 故障不影响本指示结果
 */
@Component
class RouteStoreHealthIndicator : HealthIndicator {
    @Resource
    private lateinit var routeSnapshotStore: RouteSnapshotStore

    override fun health(): Health {
        val version = routeSnapshotStore.version
        return if (version == null) {
            Health.down().withDetail("reason", "路由快照未加载, 网关处于空路由状态").build()
        } else {
            Health.up()
                .withDetail("version", version)
                .withDetail("routes", routeSnapshotStore.currentSnapshots().size)
                .build()
        }
    }
}

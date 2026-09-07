package com.zax.aspen.gateway.route

import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 网关启动时加载路由快照
 *
 * Redis 不可用或 Admin 尚未发布时不阻断启动, 以空路由运行并经健康检查暴露 DOWN,
 * 待通知到达或重新部署自愈; 配置文件静态路由不受影响, 仍合并生效
 */
@Component
class RouteStartupLoader : ApplicationRunner {
    @Resource
    private lateinit var routeSnapshotStore: RouteSnapshotStore

    @Resource
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    override fun run(args: ApplicationArguments) {
        try {
            if (routeSnapshotStore.refresh()) {
                applicationEventPublisher.publishEvent(RefreshRoutesEvent(this))
            }
        } catch (e: Exception) {
            log.error("启动路由快照加载失败, 以当前快照 version={} 继续启动", routeSnapshotStore.version, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteStartupLoader::class.java)
    }
}

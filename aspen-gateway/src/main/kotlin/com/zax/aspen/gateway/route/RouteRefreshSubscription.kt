package com.zax.aspen.gateway.route

import com.zax.aspen.admin.api.constant.GatewayRouteContract
import com.zax.aspen.common.cache.support.AspenRedisOperations
import jakarta.annotation.Resource
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 启动时把路由刷新处理器注册到 Admin 的通知频道
 *
 * 频道名按 environment 与 Admin 侧对齐; 订阅容器与生命周期由 common-cache 公共装配
 * 管理, 网络分区恢复后订阅自动重连, 错过的通知由下次变更或重启自愈
 */
@Component
class RouteRefreshSubscription : ApplicationRunner {
    @Resource
    private lateinit var aspenRedisOperations: AspenRedisOperations

    @Resource
    private lateinit var routeRefreshListener: RouteRefreshListener

    @Resource
    private lateinit var gatewayRouteProperties: GatewayRouteProperties

    override fun run(args: ApplicationArguments) {
        try {
            aspenRedisOperations.subscribe(
                GatewayRouteContract.refreshChannel(gatewayRouteProperties.environment),
                routeRefreshListener::onRefreshNotification,
            )
        } catch (e: Exception) {
            log.error("路由刷新订阅注册失败, 依赖下次通知或重启自愈", e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteRefreshSubscription::class.java)
    }
}

package com.zax.aspen.gateway.route

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 网关路由加载配置
 *
 * environment 参与 Redis Key 与频道名拼装, 必须与 Admin 侧 aspen.routes.environment
 * 配置一致, 否则读不到 Admin 发布的路由快照
 */
@Component
@ConfigurationProperties(prefix = "aspen.routes")
class GatewayRouteProperties(
    /** 部署环境标识, 参与 Redis Key 与频道名, 与 Admin 侧保持一致 */
    var environment: String = "local",
)

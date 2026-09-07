package com.zax.aspen.common.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 路由分发链路配置
 *
 * Admin 发布侧与 Gateway 消费侧共用同一配置类与同一前缀, 双方 environment
 * 取值一致时才能读写同一 Key; 部署多环境共用同一 Redis 时以该段隔离
 */
@ConfigurationProperties(prefix = "aspen.routes")
class GatewayRouteProperties(
    /** 部署环境标识, 参与 Redis Key 与频道名, Admin 与 Gateway 两侧必须一致 */
    var environment: String = "local",
)

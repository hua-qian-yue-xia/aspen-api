package com.zax.aspen.admin.biz.config.sys

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 路由发布链路配置
 *
 * environment 参与 Redis Key 与频道名拼装, 必须与 Gateway 侧 aspen.routes.environment
 * 配置一致, 否则双方读写不同 Key; 部署多环境共用同一 Redis 时以该段隔离
 */
@Component
@ConfigurationProperties(prefix = "aspen.routes")
class RoutePublishProperties(
    /** 部署环境标识, 参与 Redis Key 与频道名, 与 Gateway 侧保持一致 */
    var environment: String = "local",
)

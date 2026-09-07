package com.zax.aspen.admin.biz.messaging.redis.sys

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Admin 启动时首发路由快照
 *
 * 保证 Redis 路由 Key 可随时从权威源全量重建: Redis 被清空或发布历史失败后, 重启
 * Admin 即恢复分发; Redis 不可用不阻断启动, 记录错误等待下次变更或重启自愈
 */
@Component
class RoutePublishStartupRunner(
    private val routeDefinitionPublisher: RouteDefinitionPublisher,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        routeDefinitionPublisher.publishAllSafely()
    }
}

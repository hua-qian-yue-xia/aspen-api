package com.zax.aspen.gateway.route

import org.slf4j.LoggerFactory
import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 消费 Admin 的路由刷新通知并触发网关重载
 *
 * 通知消息体为快照版本号; 先与本地版本比对, 乱序或重复通知直接忽略, 只有采纳了
 * 新版本才发布 RefreshRoutesEvent 让 Spring Cloud Gateway 重建路由表; 通知不持久,
 * 断线期间的变更靠下次通知或重启自愈
 */
@Component
class RouteRefreshListener(
    private val routeSnapshotStore: RouteSnapshotStore,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    /**
     * 处理一条刷新通知, 消息体为快照版本号字符串
     *
     * @param message Redis Pub/Sub 通知消息体, 应为十进制快照版本号字符串, 非法时仅告警并忽略
     */
    fun onRefreshNotification(message: String) {
        val notifiedVersion = message.toLongOrNull()
        if (notifiedVersion == null) {
            log.warn("路由刷新通知版本号非法: {}", message)
            return
        }
        if (!routeSnapshotStore.isNewer(notifiedVersion)) {
            return
        }
        if (routeSnapshotStore.refresh()) {
            applicationEventPublisher.publishEvent(RefreshRoutesEvent(this))
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RouteRefreshListener::class.java)
    }
}

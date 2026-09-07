package com.zax.aspen.gateway.route

import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils

/**
 * 覆盖刷新通知的版本比对与事件触发
 */
class RouteRefreshListenerTest {
    private val routeSnapshotStore: RouteSnapshotStore = Mockito.mock(RouteSnapshotStore::class.java)
    private val applicationEventPublisher: ApplicationEventPublisher =
        Mockito.mock(ApplicationEventPublisher::class.java)

    private val listener = RouteRefreshListener().apply {
        ReflectionTestUtils.setField(this, "routeSnapshotStore", routeSnapshotStore)
        ReflectionTestUtils.setField(this, "applicationEventPublisher", applicationEventPublisher)
    }

    @Test
    fun `newer version refreshes and publishes gateway event`() {
        Mockito.`when`(routeSnapshotStore.isNewer(5L)).thenReturn(true)
        Mockito.`when`(routeSnapshotStore.refresh()).thenReturn(true)

        listener.onRefreshNotification("5")

        Mockito.verify(applicationEventPublisher).publishEvent(Mockito.any(RefreshRoutesEvent::class.java))
        Mockito.verify(routeSnapshotStore).refresh()
    }

    @Test
    fun `stale version is ignored`() {
        Mockito.`when`(routeSnapshotStore.isNewer(4L)).thenReturn(false)

        listener.onRefreshNotification("4")

        Mockito.verify(routeSnapshotStore, Mockito.never()).refresh()
        Mockito.verifyNoInteractions(applicationEventPublisher)
    }

    @Test
    fun `illegal version body is ignored`() {
        listener.onRefreshNotification("abc")

        Mockito.verifyNoInteractions(routeSnapshotStore)
        Mockito.verifyNoInteractions(applicationEventPublisher)
    }

    @Test
    fun `failed refresh does not publish gateway event`() {
        Mockito.`when`(routeSnapshotStore.isNewer(6L)).thenReturn(true)
        Mockito.`when`(routeSnapshotStore.refresh()).thenReturn(false)

        listener.onRefreshNotification("6")

        Mockito.verifyNoInteractions(applicationEventPublisher)
    }
}

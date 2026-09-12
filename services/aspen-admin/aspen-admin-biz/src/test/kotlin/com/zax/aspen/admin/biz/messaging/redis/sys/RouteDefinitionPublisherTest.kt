package com.zax.aspen.admin.biz.messaging.redis.sys

import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntityDraft
import com.zax.aspen.admin.biz.repository.sys.SysRouteRepository
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.gateway.contract.RouteDefinitionSnapshot
import com.zax.aspen.common.gateway.publish.RouteEnvelopePublisher
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * 覆盖路由发布链路的衔接: 启用行转快照、坏行跳过与故障吞并
 */
class RouteDefinitionPublisherTest {
    private val sysRouteRepository: SysRouteRepository = Mockito.mock(SysRouteRepository::class.java)
    private val routeEnvelopePublisher: RouteEnvelopePublisher = Mockito.mock(RouteEnvelopePublisher::class.java)

    private val publisher = RouteDefinitionPublisher(sysRouteRepository, routeEnvelopePublisher)

    @Test
    fun `publishes enabled rows through envelope publisher`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(
            listOf(routeEntity(routeId = 1L, routeCode = "aspen-admin"), routeEntity(routeId = 2L, routeCode = "demo-service")),
        )
        val published = capturePublishedOn(version = 7L)

        val version = publisher.publishAll()

        assertEquals(7L, version)
        assertEquals(listOf("aspen-admin", "demo-service"), published.flatMap { snapshot -> snapshot.map { it.routeCode } })
    }

    @Test
    fun `skips illegal rows instead of blocking the snapshot`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(
            listOf(
                routeEntity(routeId = 1L, routeCode = "aspen-admin"),
                routeEntity(routeId = 2L, routeCode = "broken-route", uri = "ftp://broken"),
                routeEntity(routeId = 3L, routeCode = "empty-predicates", predicates = emptyList()),
            ),
        )
        val published = capturePublishedOn(version = 3L)

        publisher.publishAll()

        assertEquals(listOf("aspen-admin"), published.flatMap { snapshot -> snapshot.map { it.routeCode } })
    }

    /** 验证无启用路由时仍以空列表发布空信封, 清空路由是合法的发布形态 */
    @Test
    fun `publishes empty envelope when no enabled routes remain`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(emptyList())
        val published = capturePublishedOn(version = 9L)

        val version = publisher.publishAll()

        assertEquals(9L, version)
        assertEquals(listOf(emptyList<RouteDefinitionSnapshot>()), published)
    }

    /**
     * 在发布原语上捕获全部调用入参并固定返回版本
     *
     * @param version 桩定的发布版本号
     * @return 每次调用的路由快照列表, 供断言按调用检查发布内容
     */
    @Suppress("UNCHECKED_CAST")
    private fun capturePublishedOn(version: Long): List<List<RouteDefinitionSnapshot>> {
        val published = mutableListOf<List<RouteDefinitionSnapshot>>()
        Mockito.doAnswer { invocation ->
            published.add(invocation.getArgument(0) as List<RouteDefinitionSnapshot>)
            version
        }.`when`(routeEnvelopePublisher).publishAll(Mockito.anyList())
        return published
    }

    @Test
    fun `redis failure is swallowed for non interruptible callers`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(listOf(routeEntity()))
        Mockito.`when`(routeEnvelopePublisher.publishAll(Mockito.anyList()))
            .thenThrow(IllegalStateException("redis down"))

        publisher.publishAllSafely()
    }

    /**
     * 构造已落库形态的启用路由行 fixture, 列值与真实写入的类型化形态一致
     *
     * @param routeId 路由主键, 默认 1L
     * @param routeCode 路由编码, 默认 aspen-admin, 传非法 uri 的编码用于构造坏行
     * @param uri 目标地址, 默认合法的 lb 协议, 传非法协议触发快照构造失败
     * @param predicates 路由断言列表, 默认一条 Path 断言, 传空列表用于构造无断言坏行
     * @return 固定启用状态的 SysRouteEntity
     */
    private fun routeEntity(
        routeId: Long = 1L,
        routeCode: String = "aspen-admin",
        uri: String = "lb://aspen-admin",
        predicates: List<com.zax.aspen.common.gateway.contract.RouteDefinitionPart> = listOf(
            com.zax.aspen.common.gateway.contract.RouteDefinitionPart("Path", mapOf("_genkey_0" to "/admin/**")),
        ),
    ): SysRouteEntity =
        SysRouteEntityDraft.`$`.produce {
            this.routeId = routeId
            this.routeCode = routeCode
            routeName = "演示服务"
            this.uri = uri
            this.predicates = predicates
            filters = emptyList()
            metadata = null
            sortOrder = 0
            status = EnabledStatus.ENABLED
            version = 1
            createdAt = LocalDateTime.of(2026, 9, 7, 10, 0)
            updatedAt = LocalDateTime.of(2026, 9, 7, 10, 0)
        }
}

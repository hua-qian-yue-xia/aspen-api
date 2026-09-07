package com.zax.aspen.admin.biz.service.sys

import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntityDraft
import com.zax.aspen.admin.biz.messaging.redis.sys.SysRouteChangedEvent
import com.zax.aspen.admin.biz.repository.sys.SysRouteRepository
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖路由管理的校验分支与发布事件编排
 */
class SysRouteServiceTest {
    private val sysRouteRepository: SysRouteRepository = Mockito.mock(SysRouteRepository::class.java)
    private val eventPublisher: ApplicationEventPublisher = Mockito.mock(ApplicationEventPublisher::class.java)

    private val service = SysRouteService().apply {
        ReflectionTestUtils.setField(this, "sysRouteRepository", sysRouteRepository)
        ReflectionTestUtils.setField(this, "eventPublisher", eventPublisher)
    }

    @Test
    fun `create route publishes changed event and returns view`() {
        val command = command()
        Mockito.`when`(sysRouteRepository.findByCode("demo-service")).thenReturn(null)
        Mockito.`when`(sysRouteRepository.insert(command, "internal:route-api"))
            .thenReturn(routeEntity(routeId = 9L, routeCode = "demo-service"))

        val view = service.createRoute(command)

        assertEquals("demo-service", view.routeCode)
        assertEquals(listOf(Pair("Path", "/demo/**")), view.predicates.map { it.name to it.args["_genkey_0"] })
        Mockito.verify(eventPublisher).publishEvent(SysRouteChangedEvent)
    }

    @Test
    fun `create route rejects duplicated code`() {
        Mockito.`when`(sysRouteRepository.findByCode("demo-service")).thenReturn(routeEntity())

        assertFailsWith<IllegalArgumentException> { service.createRoute(command()) }
        Mockito.verifyNoInteractions(eventPublisher)
    }

    @Test
    fun `create route rejects illegal uri scheme`() {
        assertFailsWith<IllegalArgumentException> { service.createRoute(command(uri = "ftp://demo-service")) }
    }

    @Test
    fun `create route rejects blank name and negative order and empty predicates`() {
        assertFailsWith<IllegalArgumentException> { service.createRoute(command(routeName = " ")) }
        assertFailsWith<IllegalArgumentException> { service.createRoute(command(sortOrder = -1)) }
        assertFailsWith<IllegalArgumentException> { service.createRoute(command(predicates = emptyList())) }
    }

    @Test
    fun `update route rejects code change and missing route`() {
        Mockito.`when`(sysRouteRepository.findById(1L)).thenReturn(routeEntity(routeId = 1L, routeCode = "demo-service"))

        assertFailsWith<IllegalArgumentException> { service.updateRoute(1L, command(routeCode = "other-service")) }
        assertFailsWith<IllegalArgumentException> { service.updateRoute(404L, command()) }
    }

    @Test
    fun `delete route rejects missing route and publishes on success`() {
        Mockito.`when`(sysRouteRepository.findById(404L)).thenReturn(null)
        assertFailsWith<IllegalArgumentException> { service.deleteRoute(404L) }

        val existing = routeEntity()
        Mockito.`when`(sysRouteRepository.findById(1L)).thenReturn(existing)
        service.deleteRoute(1L)
        Mockito.verify(sysRouteRepository).delete(existing)
        Mockito.verify(eventPublisher).publishEvent(SysRouteChangedEvent)
    }

    @Test
    fun `list routes maps typed columns into views`() {
        Mockito.`when`(sysRouteRepository.findAll()).thenReturn(listOf(routeEntity()))

        val views = service.listRoutes()

        assertEquals(1, views.size)
        assertEquals("/demo/**", views.single().predicates.single().args["_genkey_0"])
        assertEquals(true, views.single().metadata.isEmpty())
    }

    /**
     * 构造 demo-service 路由的写入命令夹具
     *
     * @param routeCode 路由编码, 默认 demo-service, 更新时传其他编码触发编码不可变校验
     * @param routeName 路由名称, 传空白值触发名称校验
     * @param uri 路由目标地址, 默认合法的 lb 协议, 传非法协议触发协议校验
     * @param sortOrder 排序权重, 传负数触发排序校验
     * @param predicates 路由谓词列表, 传空列表触发谓词校验
     * @return 状态固定为启用的写入命令
     */
    private fun command(
        routeCode: String = "demo-service",
        routeName: String = "演示服务",
        uri: String = "lb://demo-service",
        sortOrder: Int = 0,
        predicates: List<RouteDefinitionPart> = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**"))),
    ): SysRouteSaveDTO =
        SysRouteSaveDTO(
            routeCode = routeCode,
            routeName = routeName,
            uri = uri,
            sortOrder = sortOrder,
            status = EnabledStatus.ENABLED,
            predicates = predicates,
        )

    /**
     * 构造已落库形态的路由行 fixture, 列值与服务写入的类型化形态一致
     *
     * @param routeId 路由主键, 默认 1L
     * @param routeCode 路由编码, 默认 demo-service
     * @return 固定启用状态的 SysRouteEntity
     */
    private fun routeEntity(
        routeId: Long = 1L,
        routeCode: String = "demo-service",
    ): SysRouteEntity =
        SysRouteEntityDraft.`$`.produce {
            this.routeId = routeId
            this.routeCode = routeCode
            routeName = "演示服务"
            uri = "lb://demo-service"
            predicates = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**")))
            filters = emptyList()
            metadata = null
            sortOrder = 0
            status = EnabledStatus.ENABLED
            version = 1
            createdAt = LocalDateTime.of(2026, 9, 6, 10, 0)
            updatedAt = LocalDateTime.of(2026, 9, 6, 10, 0)
        }
}

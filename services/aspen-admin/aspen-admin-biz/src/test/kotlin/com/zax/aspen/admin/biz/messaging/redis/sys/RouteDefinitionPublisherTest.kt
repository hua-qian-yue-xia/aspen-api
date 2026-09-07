package com.zax.aspen.admin.biz.messaging.redis.sys

import com.zax.aspen.admin.api.event.sys.RouteCatalogSnapshot
import com.zax.aspen.admin.api.event.sys.RouteDefinitionPart
import com.zax.aspen.admin.biz.config.sys.RoutePublishProperties
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntityDraft
import com.zax.aspen.admin.biz.repository.sys.SysRouteRepository
import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.core.enums.common.EnabledStatus
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖路由快照发布的取号、原子替换、坏行跳过与故障吞并
 */
class RouteDefinitionPublisherTest {
    private val sysRouteRepository: SysRouteRepository = Mockito.mock(SysRouteRepository::class.java)
    private val aspenRedisOperations: AspenRedisOperations = Mockito.mock(AspenRedisOperations::class.java)

    private val objectMapper: ObjectMapper =
        JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val properties = RoutePublishProperties()

    private val publisher = RouteDefinitionPublisher(
        sysRouteRepository = sysRouteRepository,
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        routePublishProperties = properties,
        clock = Clock.fixed(Instant.parse("2026-09-06T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
    )

    @Test
    fun `publishes versioned envelope and notifies channel`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(
            listOf(routeEntity(routeId = 1L, routeCode = "aspen-admin"), routeEntity(routeId = 2L, routeCode = "demo-service")),
        )
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version")).thenReturn(7L)

        val version = publisher.publishAll()

        assertEquals(7L, version)
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValue(eqText("aspen:local:gateway:routes"), captureText(captor))
        val envelope = objectMapper.readValue<RouteCatalogSnapshot>(captor.value)
        assertEquals(7L, envelope.version)
        assertEquals("2026-09-06T10:00+08:00", envelope.publishedAt)
        assertEquals(listOf("aspen-admin", "demo-service"), envelope.routes.map { it.routeCode })
        Mockito.verify(aspenRedisOperations).publish("aspen:local:gateway:routes:refresh", "7")
    }

    @Test
    fun `skips illegal route rows instead of blocking the snapshot`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(
            listOf(
                routeEntity(routeId = 1L, routeCode = "aspen-admin"),
                routeEntity(routeId = 2L, routeCode = "broken-route", uri = "ftp://broken"),
                routeEntity(routeId = 3L, routeCode = "empty-predicates", predicates = emptyList()),
            ),
        )
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version")).thenReturn(8L)

        val version = publisher.publishAll()

        assertEquals(8L, version)
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValue(eqText("aspen:local:gateway:routes"), captureText(captor))
        val envelope = objectMapper.readValue<RouteCatalogSnapshot>(captor.value)
        assertEquals(listOf("aspen-admin"), envelope.routes.map { it.routeCode })
    }

    @Test
    fun `publishes empty envelope when no enabled routes remain`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(emptyList())
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version")).thenReturn(9L)

        assertEquals(9L, publisher.publishAll())

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValue(eqText("aspen:local:gateway:routes"), captureText(captor))
        assertEquals(0, objectMapper.readValue<RouteCatalogSnapshot>(captor.value).routes.size)
    }

    @Test
    fun `safe publish swallows redis failure`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(emptyList())
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version"))
            .thenThrow(IllegalStateException("redis down"))

        publisher.publishAllSafely()
    }

    @Test
    fun `publish fails fast on missing version increment`() {
        Mockito.`when`(sysRouteRepository.findAllEnabled()).thenReturn(emptyList())
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version"))
            .thenThrow(IllegalArgumentException("版本计数器取号失败"))

        assertFailsWith<IllegalArgumentException> { publisher.publishAll() }
    }

    /**
     * eq matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param value 期望匹配的实参值
     * @return matcher 登记结果, matcher 返回 null 时回退为原值
     */
    private fun eqText(value: String): String = ArgumentMatchers.eq(value) ?: value

    /**
     * capture matcher 的非空包装: Kotlin 非空参数不接受 matcher 返回的 null
     *
     * @param captor 用于捕获实参的字符串捕获器
     * @return matcher 登记结果, matcher 返回 null 时回退为空字符串
     */
    private fun captureText(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""

    /**
     * 构造固定启用形态的路由行 fixture, 其余列取演示服务的典型值
     *
     * @param routeId 路由主键
     * @param routeCode 路由编码
     * @param uri 路由目标地址, 默认合法的 lb 协议, 传非法协议用于构造坏行
     * @param predicates 路由谓词列表, 默认匹配 /demo/ 前缀的 Path 谓词, 传空列表用于构造坏行
     * @return 可作为 findAllEnabled 返回值的 SysRouteEntity
     */
    private fun routeEntity(
        routeId: Long,
        routeCode: String,
        uri: String = "lb://demo-service",
        predicates: List<RouteDefinitionPart> = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**"))),
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
            createdAt = LocalDateTime.of(2026, 9, 6, 10, 0)
            updatedAt = LocalDateTime.of(2026, 9, 6, 10, 0)
    }
}

package com.zax.aspen.common.gateway.publish

import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.gateway.GatewayRouteProperties
import com.zax.aspen.common.gateway.contract.RouteCatalogSnapshot
import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
import com.zax.aspen.common.gateway.contract.RouteDefinitionSnapshot
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
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 覆盖路由信封发布的取号、原子替换与通知的介质操作语义 */
class RouteEnvelopePublisherTest {
    private val aspenRedisOperations: AspenRedisOperations =
        Mockito.mock(AspenRedisOperations::class.java)

    private val objectMapper: ObjectMapper =
        JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val publisher = RouteEnvelopePublisher(
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        properties = GatewayRouteProperties(),
        clock = Clock.fixed(Instant.parse("2026-09-07T02:00:00Z"), ZoneId.of("Asia/Shanghai")),
    )

    /** 验证一次发布完成取号、信封原子替换与携带版本号的通知, 且信封内容完整 */
    @Test
    fun `publishes versioned envelope and notifies channel`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version")).thenReturn(7L)

        val version = publisher.publishAll(listOf(route("aspen-admin"), route("demo-service")))

        assertEquals(7L, version)
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValue(eqText("aspen:local:gateway:routes"), captureText(captor))
        val envelope = objectMapper.readValue<RouteCatalogSnapshot>(captor.value)
        assertEquals(7L, envelope.version)
        assertEquals("2026-09-07T10:00+08:00", envelope.publishedAt)
        assertEquals(listOf("aspen-admin", "demo-service"), envelope.routes.map { it.routeCode })
        Mockito.verify(aspenRedisOperations).publish("aspen:local:gateway:routes:refresh", "7")
    }

    /** 验证空路由列表仍发布空信封, 清空全部路由是合法的发布形态 */
    @Test
    fun `publishes empty envelope when no routes remain`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version")).thenReturn(9L)

        val version = publisher.publishAll(emptyList())

        assertEquals(9L, version)
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aspenRedisOperations).setValue(eqText("aspen:local:gateway:routes"), captureText(captor))
        assertEquals(0, objectMapper.readValue<RouteCatalogSnapshot>(captor.value).routes.size)
    }

    /** 验证取号失败时快速失败, 不产生信封写入与通知 */
    @Test
    fun `fails fast when version increment fails`() {
        Mockito.`when`(aspenRedisOperations.increment("aspen:local:gateway:routes:version"))
            .thenThrow(IllegalArgumentException("版本计数器取号失败"))

        assertFailsWith<IllegalArgumentException> { publisher.publishAll(emptyList()) }

        Mockito.verify(aspenRedisOperations, Mockito.never())
            .setValue(Mockito.anyString(), Mockito.anyString())
        Mockito.verify(aspenRedisOperations, Mockito.never())
            .publish(Mockito.anyString(), Mockito.anyString())
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
     * 构造可进入信封的合法路由快照 fixture
     *
     * @param routeCode 路由编码
     * @return 携带 Path 断言、可正常序列化的路由定义快照
     */
    private fun route(routeCode: String): RouteDefinitionSnapshot =
        RouteDefinitionSnapshot(
            routeCode = routeCode,
            uri = "lb://demo-service",
            order = 0,
            predicates = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**"))),
            filters = emptyList(),
        )
}

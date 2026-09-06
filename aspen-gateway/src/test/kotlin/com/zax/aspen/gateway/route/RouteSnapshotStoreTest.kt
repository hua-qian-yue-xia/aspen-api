package com.zax.aspen.gateway.route

import com.zax.aspen.common.cache.support.AspenRedisOperations
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 覆盖路由快照的采纳、乱序忽略与降级保留语义
 */
class RouteSnapshotStoreTest {
    private val aspenRedisOperations: AspenRedisOperations =
        Mockito.mock(AspenRedisOperations::class.java)

    private val store = RouteSnapshotStore().apply {
        ReflectionTestUtils.setField(this, "aspenRedisOperations", aspenRedisOperations)
        ReflectionTestUtils.setField(
            this,
            "objectMapper",
            JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(),
        )
        ReflectionTestUtils.setField(this, "gatewayRouteProperties", GatewayRouteProperties())
    }

    @Test
    fun `adopts newer envelope and exposes snapshots`() {
        stubGet(envelope(version = 3, routes = listOf(route("aspen-admin"))))

        assertTrue(store.refresh())

        assertEquals(3L, store.version)
        assertEquals(listOf("aspen-admin"), store.currentSnapshots().map { it.routeCode })
        assertTrue(store.isNewer(4))
        assertFalse(store.isNewer(3))
    }

    @Test
    fun `ignores stale version without touching local snapshot`() {
        stubGet(envelope(version = 3, routes = listOf(route("aspen-admin"))))
        store.refresh()

        stubGet(envelope(version = 2, routes = listOf(route("other-service"))))
        assertFalse(store.refresh())

        assertEquals(3L, store.version)
        assertEquals(listOf("aspen-admin"), store.currentSnapshots().map { it.routeCode })
    }

    @Test
    fun `keeps previous snapshot when key is missing or broken`() {
        stubGet(envelope(version = 3, routes = listOf(route("aspen-admin"))))
        store.refresh()

        stubGet(null)
        assertFalse(store.refresh())

        stubGet("not-json")
        assertFalse(store.refresh())

        assertEquals(3L, store.version)
    }

    @Test
    fun `keeps previous snapshot when routes node is not an array`() {
        stubGet(envelope(version = 3, routes = listOf(route("aspen-admin"))))
        store.refresh()

        stubGet("""{"version":6,"publishedAt":"2026-09-06T11:00+08:00","routes":"corrupted"}""")

        assertFalse(store.refresh())

        assertEquals(3L, store.version)
        assertEquals(listOf("aspen-admin"), store.currentSnapshots().map { it.routeCode })
    }

    @Test
    fun `skips illegal single route but adopts the rest`() {
        stubGet(
            envelope(
                version = 5,
                routes = listOf(
                    route("aspen-admin"),
                    route("broken-route", uri = "ftp://broken"),
                ),
            ),
        )

        assertTrue(store.refresh())

        assertEquals(5L, store.version)
        assertEquals(listOf("aspen-admin"), store.currentSnapshots().map { it.routeCode })
    }

    @Test
    fun `redis outage keeps local snapshot`() {
        stubGet(envelope(version = 3, routes = listOf(route("aspen-admin"))))
        store.refresh()

        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:gateway:routes"))
            .thenThrow(IllegalStateException("redis down"))

        assertFalse(store.refresh())
        assertEquals(3L, store.version)
    }

    /**
     * 把路由快照 key 的 Redis 读取桩定为返回指定文本
     *
     * @param text 待返回的信封文本, 传 `null` 模拟 key 不存在, 传非 JSON 文本模拟脏数据
     */
    private fun stubGet(text: String?) {
        Mockito.`when`(aspenRedisOperations.getValue("aspen:local:gateway:routes")).thenReturn(text)
    }

    /**
     * 拼接指定版本与路由集合的快照信封 JSON
     *
     * @param version 信封携带的版本号
     * @param routes 已序列化的路由 JSON 列表, 按传入顺序进入 routes 数组
     * @return 可作为 Redis 快照值写入的完整信封文本
     */
    private fun envelope(version: Long, routes: List<String>): String {
        val routeJson = routes.joinToString(",") { it }
        return """{"version":$version,"publishedAt":"2026-09-06T10:00+08:00","routes":[$routeJson]}"""
    }

    /**
     * 构造单条路由的 JSON 文本, 固定携带匹配 /demo/ 前缀的 Path 谓词
     *
     * @param routeCode 路由编码
     * @param uri 路由目标地址, 默认合法的 lb 协议, 传非法协议用于构造坏行
     * @return 可拼入信封 routes 数组的路由 JSON
     */
    private fun route(routeCode: String, uri: String = "lb://demo-service"): String =
        """{"routeCode":"$routeCode","uri":"$uri","order":0,"predicates":[{"name":"Path","args":{"_genkey_0":"/demo/**"}}],"filters":[],"metadata":{}}"""
}

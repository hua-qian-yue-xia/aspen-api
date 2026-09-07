package com.zax.aspen.common.gateway.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证路由发布快照在构造期拒绝协议与语法非法的目标地址 */
class RouteDefinitionSnapshotTest {
    /**
     * 构造一条携带 Path 断言的合法路由
     *
     * @param routeCode 路由编码, 默认 aspen-admin
     * @param uri 目标地址, 默认 lb://aspen-admin
     * @return 可通过构造校验的路由定义快照
     */
    private fun snapshot(routeCode: String = "aspen-admin", uri: String = "lb://aspen-admin") =
        RouteDefinitionSnapshot(
            routeCode = routeCode,
            uri = uri,
            order = 0,
            predicates = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/demo/**"))),
            filters = emptyList(),
        )

    /** 验证合法路由可以构造且字段原样保留 */
    @Test
    fun `accepts legal uri schemes`() {
        listOf("lb://aspen-admin", "http://10.0.0.1:8080", "https://example.com/base").forEach { uri ->
            assertEquals(uri, snapshot(uri = uri).uri)
        }
    }

    /** 验证协议不在白名单内的目标地址被拒绝 */
    @Test
    fun `rejects unsupported uri schemes`() {
        val error = assertFailsWith<IllegalArgumentException> { snapshot(uri = "ftp://host") }
        assertTrue(error.message!!.contains("lb://"))
    }

    /** 验证语法非法的 uri 在构造期即失败, 不进入分发介质 */
    @Test
    fun `rejects syntactically illegal uri before publishing`() {
        val error = assertFailsWith<IllegalArgumentException> { snapshot(uri = "lb://bad host/ x") }
        assertTrue(error.message!!.contains("语法非法"))
    }

    /** 验证空断言路由被拒绝, 防止兜住全部请求的危险配置 */
    @Test
    fun `rejects route without predicates`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RouteDefinitionSnapshot(
                routeCode = "aspen-admin",
                uri = "lb://aspen-admin",
                order = 0,
                predicates = emptyList(),
                filters = emptyList(),
            )
        }
        assertTrue(error.message!!.contains("断言"))
    }
}

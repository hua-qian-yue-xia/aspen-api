package com.zax.aspen.common.web.route

import com.zax.aspen.common.route.PostRoute
import com.zax.aspen.common.web.fixture.controller.admin.route.FixtureRouteApi
import org.springframework.web.method.HandlerMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 路由声明解析器的单元验证
 *
 * HandlerMethod 的方法取实现类 (Controller 零注解), 路由注解声明在契约接口上,
 * 验证接口层查找与负结果缓存语义
 */
class RouteSpecResolverTest {
    /** 验证实现方法向契约接口层查找并归一路由声明 */
    @Test
    fun `resolves spec declared on contract interface`() {
        val handler = HandlerMethod(FixtureRouteControllerStub(), FixtureRouteControllerStub::class.java.getMethod("echo"))

        val spec = RouteSpecResolver().resolve(handler)

        assertEquals(PostRoute::class, spec?.verb)
        assertEquals("回显样例", spec?.summary)
        assertEquals(2, spec?.rateLimit?.limit)
        assertEquals("INSERT", spec?.log?.name)
    }

    /** 验证无路由注解的 handler 解析为 null, 未注解端点走原生 MVC 路径 */
    @Test
    fun `returns null for unannotated handler`() {
        val handler = HandlerMethod(PlainHandler(), PlainHandler::class.java.getMethod("handle"))

        assertNull(RouteSpecResolver().resolve(handler))
    }

    /** 契约接口的零注解实现, 模拟真实 Controller 形态 */
    private class FixtureRouteControllerStub : FixtureRouteApi {
        override fun echo(): String = "ok"

        override fun limited(): String = "limited"

        override fun detail(): String = "detail"
    }

    /** 无任何路由注解的普通 handler */
    private class PlainHandler {
        /** @return 固定文本 */
        fun handle(): String = "plain"
    }
}

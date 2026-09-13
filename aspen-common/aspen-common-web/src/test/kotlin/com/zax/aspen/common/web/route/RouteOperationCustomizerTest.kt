package com.zax.aspen.common.web.route

import com.zax.aspen.common.web.fixture.controller.admin.route.FixtureRouteApi
import io.swagger.v3.oas.models.Operation
import org.springframework.web.method.HandlerMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * springdoc 文档定制的单元验证
 *
 * 验证路由注解的 summary/description 被写入 OpenAPI Operation, 未注解端点保持原样
 */
class RouteOperationCustomizerTest {
    /** 验证注解端点的 summary 与 description 写入文档 */
    @Test
    fun `writes summary and description for annotated handler`() {
        val handler = HandlerMethod(CustomizerStub(), CustomizerStub::class.java.getMethod("echo"))

        val operation = RouteOperationCustomizer(RouteSpecResolver())
            .customize(Operation().description("已有说明会被注解值覆盖"), handler)

        assertEquals("回显样例", operation.summary)
        assertEquals("补充说明", operation.description)
    }

    /** 验证未注解端点不被改写 */
    @Test
    fun `leaves unannotated handler untouched`() {
        val handler = HandlerMethod(CustomizerStub(), CustomizerStub::class.java.getMethod("describe"))

        val operation = RouteOperationCustomizer(RouteSpecResolver()).customize(Operation(), handler)

        assertNull(operation.summary)
        assertNull(operation.description)
    }

    /** 携带文档声明的契约接口实现, 提供正反两个 handler */
    private class CustomizerStub : DocumentedApi {
        override fun echo(): String = "ok"

        override fun describe(): String = "plain"
    }

    /** 文档定制专用契约: echo 携带完整文档声明, describe 未注解 */
    private interface DocumentedApi {
        /**
         * 带完整声明的端点
         *
         * @return 固定文本
         */
        @com.zax.aspen.common.route.PostRoute("/documented/echo", summary = "回显样例", description = "补充说明")
        fun echo(): String

        /**
         * 未注解端点
         *
         * @return 固定文本
         */
        fun describe(): String
    }
}

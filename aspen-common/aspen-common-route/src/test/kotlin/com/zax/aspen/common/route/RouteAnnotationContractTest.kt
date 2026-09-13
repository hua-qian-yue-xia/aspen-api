package com.zax.aspen.common.route

import org.springframework.core.annotation.AliasFor
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 路由套件注解的契约验证
 *
 * 锁定五个 verb 组合注解与 Spring MVC 的接线: 元注解 @RequestMapping 的方法值
 * 与各自动词一致, path 属性经 @AliasFor 转发给 RequestMapping.path, MVC 映射注册
 * 与 IDE 解析依赖该接线; 端到端映射验证在 common-web 的 RouteSuiteTest
 */
class RouteAnnotationContractTest {
    /** 验证五个 verb 注解元标注 RequestMapping 且方法值与动词一致 */
    @Test
    fun `verb annotations meta annotate RequestMapping with matching method`() {
        val expectedMethods = mapOf(
            "GetRoute" to "GET",
            "PostRoute" to "POST",
            "PutRoute" to "PUT",
            "DeleteRoute" to "DELETE",
            "PatchRoute" to "PATCH",
        )
        val annotationClasses = listOf(
            GetRoute::class,
            PostRoute::class,
            PutRoute::class,
            DeleteRoute::class,
            PatchRoute::class,
        )

        annotationClasses.forEach { annotationClass ->
            val requestMapping = annotationClass.java.getAnnotation(RequestMapping::class.java)
            assertNotNull(requestMapping, "${annotationClass.simpleName} 必须元标注 @RequestMapping")
            assertEquals(
                expectedMethods.getValue(annotationClass.simpleName!!),
                requestMapping.method.single().name,
                "${annotationClass.simpleName} 的 HTTP 方法与动词一致",
            )
        }
    }

    /** 验证 path 属性经 @AliasFor 转发, 注解值直达 MVC 映射注册 */
    @Test
    fun `path attribute aliases RequestMapping path`() {
        val pathAttribute = PostRoute::class.java.getMethod("path")

        val alias = pathAttribute.getAnnotation(AliasFor::class.java)

        assertNotNull(alias, "path 属性必须携带 @AliasFor")
        assertEquals(RequestMapping::class.java, alias.annotation.java)
        assertEquals("path", alias.attribute)
    }

    /** 验证 summary 必填、其余属性缺省值与文档声明一致 */
    @Test
    fun `attribute defaults match documented contract`() {
        val route = SampleAnnotated::class.java.getMethod("sample").getAnnotation(PostRoute::class.java)

        assertNotNull(route)
        assertEquals("", route.description)
        assertEquals(60, route.rateLimit.windowSeconds)
        assertEquals(100, route.rateLimit.limit)
        assertEquals(RateLimitScope.USER, route.rateLimit.scope)
        assertEquals(OperationTag.OTHER, route.log)
        assertNull(SampleAnnotated::class.java.getMethod("sample").getAnnotation(GetRoute::class.java))
    }

    /** 契约断言的宿主, 只为反射读取注解缺省值 */
    @Suppress("unused")
    private class SampleAnnotated {
        /** @return 固定文本 */
        @PostRoute("/sample", summary = "样例")
        fun sample(): String = "sample"
    }
}

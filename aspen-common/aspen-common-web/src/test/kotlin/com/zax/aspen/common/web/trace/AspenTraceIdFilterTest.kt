package com.zax.aspen.common.web.trace

import com.zax.aspen.common.web.fixture.FixtureApplication
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证 Trace ID 过滤器的请求级行为
 *
 * 断言缺失请求头时本地生成、合法外来值复用回显、非法值 (含换行/空白,
 * 有日志注入风险) 拒绝并替换, 以及成功响应同样回写响应头;
 * 失败 body 内 traceId 与响应头一致在错误契约测试中另行覆盖
 */
@SpringBootTest(classes = [FixtureApplication::class])
@AutoConfigureMockMvc
class AspenTraceIdFilterTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 验证缺失请求头时生成 16 位十六进制 traceId 并回写响应头 */
    @Test
    fun `generates traceId when header absent`() {
        val result = mockMvc.get("/fixture").andReturn()

        val header = result.response.getHeader(TraceId.HEADER)
        assertNotNull(header)
        assertTrue(Regex("^[0-9a-f]{16}$").matches(header), "生成的 traceId 应为 16 位小写十六进制: $header")
    }

    /** 验证合法外来 traceId 被复用并原样回显 */
    @Test
    fun `reuses valid incoming traceId`() {
        val result = mockMvc.get("/fixture") {
            header(TraceId.HEADER, "gw-20260912-0001")
        }.andReturn()

        assertEquals("gw-20260912-0001", result.response.getHeader(TraceId.HEADER))
    }

    /** 验证含换行与空格的非法 traceId 被拒绝并替换为生成值 */
    @Test
    fun `rejects unsafe incoming traceId`() {
        val result = mockMvc.get("/fixture") {
            header(TraceId.HEADER, "bad\nid 1")
        }.andReturn()

        val header = result.response.getHeader(TraceId.HEADER)
        assertNotNull(header)
        assertTrue(Regex("^[0-9a-f]{16}$").matches(header), "非法外来值必须替换为生成值: $header")
    }

    /** 验证失败响应 body 内 traceId 与响应头一致 */
    @Test
    fun `error body traceId matches response header`() {
        val result = mockMvc.get("/error/conflict").andReturn()

        val header = result.response.getHeader(TraceId.HEADER)
        assertNotNull(header)
        assertTrue(
            result.response.contentAsString.contains("\"traceId\":\"$header\""),
            "body traceId 应与响应头一致: ${result.response.contentAsString}",
        )
    }

    /** 验证请求结束后 MDC 被清理, finally 防线程池复用串号是过滤器的核心理由 */
    @Test
    fun `clears MDC after request completes`() {
        mockMvc.get("/fixture").andReturn()

        assertNull(MDC.get(TraceId.MDC_KEY), "请求结束后 MDC traceId 必须被清理, 防止线程池复用串号")
    }
}

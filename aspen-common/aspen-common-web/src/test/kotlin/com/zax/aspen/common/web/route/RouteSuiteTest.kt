package com.zax.aspen.common.web.route

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.zax.aspen.common.web.fixture.FixtureApplication
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 路由套件消费端的端到端验证
 *
 * 零配置启动 fixture 上下文, 验证契约接口上的 verb 组合注解被 MVC 解析为映射
 * (含 /admin-api 受众前缀)、限流超限经统一错误契约渲染 429 Problem Details、
 * 操作日志拦截器以 aspen.operation logger 产出结构化日志行 (含被限流拒绝的请求)
 */
@SpringBootTest(classes = [FixtureApplication::class])
@AutoConfigureMockMvc
class RouteSuiteTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val operationLogAppender = ListAppender<ILoggingEvent>()

    /** 挂接操作日志采集, 断言后卸载避免影响其余测试 */
    @AfterTest
    fun detachAppender() {
        (LoggerFactory.getLogger("aspen.operation") as Logger).detachAppender(operationLogAppender)
    }

    /** 验证契约接口注解端点被映射且携带受众前缀 */
    @Test
    fun `route annotated contract endpoint is mapped with audience prefix`() {
        mockMvc.get("/admin-api/suite/detail").andExpect {
            status { isOk() }
            jsonPath("$") { value("detail") }
        }
    }

    /** 验证限流配额内放行, 超限渲染 429 Problem Details 并携带机器码 */
    @Test
    fun `rate limit rejects third request as problem details`() {
        attachOperationLogAppender()

        mockMvc.post("/admin-api/suite/echo").andExpect { status { isOk() } }
        mockMvc.post("/admin-api/suite/echo").andExpect { status { isOk() } }
        mockMvc.post("/admin-api/suite/echo").andExpect {
            status { isTooManyRequests() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("COMMON.TOO_MANY_REQUESTS") }
            jsonPath("$.traceId") { exists() }
        }
    }

    /** 验证操作日志行包含分类标签、摘要与状态码, 且覆盖被限流拒绝的请求 */
    @Test
    fun `operation log captures annotated and rejected requests`() {
        attachOperationLogAppender()

        mockMvc.get("/admin-api/suite/detail").andExpect { status { isOk() } }
        mockMvc.post("/admin-api/suite/limited").andExpect { status { isOk() } }
        mockMvc.post("/admin-api/suite/limited").andExpect { status { isOk() } }
        mockMvc.post("/admin-api/suite/limited").andExpect { status { isTooManyRequests() } }

        val messages = operationLogAppender.list.map { it.formattedMessage }
        assertEquals(4, messages.size, "每个完成的注解端点请求各一行, 含被限流拒绝的请求")
        assertTrue(messages[0].contains("tag=OTHER") && messages[0].contains("summary=详情样例"), messages[0])
        assertTrue(messages[0].contains("status=200") && messages[0].contains("pattern=/admin-api/suite/detail"), messages[0])
        assertTrue(messages.last().contains("tag=EXPORT") && messages.last().contains("status=429"), messages.last())
        assertTrue(messages.all { it.contains("costMs=") && it.contains("traceId=") }, "全部日志行携带耗时与排查标识")
    }

    /**
     * 挂接 aspen.operation logger 的采集器
     *
     * 同一 JVM 内 Spring 上下文与限流计数按类缓存, 本类限流断言依赖请求顺序,
     * 因此采集器在各测试内独立挂接, 卸载统一在 [detachAppender]
     */
    private fun attachOperationLogAppender() {
        operationLogAppender.start()
        (LoggerFactory.getLogger("aspen.operation") as Logger).addAppender(operationLogAppender)
    }
}

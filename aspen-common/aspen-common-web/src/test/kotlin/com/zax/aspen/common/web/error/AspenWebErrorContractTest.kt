package com.zax.aspen.common.web.error

import com.zax.aspen.common.web.fixture.FixtureApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证统一错误契约的端到端渲染
 *
 * MockMvc 触发样例 Controller 的各类异常, 断言响应为 application/problem+json、
 * 状态与错误码映射一致、code/traceId 扩展字段存在, 且兜底异常不泄露内部细节
 */
@SpringBootTest(classes = [FixtureApplication::class])
@AutoConfigureMockMvc
class AspenWebErrorContractTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 验证业务异常渲染为精确状态 + code/traceId 扩展字段 */
    @Test
    fun `business exception renders problem details with code and traceId`() {
        mockMvc.get("/error/conflict").andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("COMMON.STATE_CONFLICT") }
            jsonPath("$.title") { value("资源状态不允许执行当前操作") }
            jsonPath("$.detail") { value("路由已停用, 拒绝修改") }
            jsonPath("$.traceId") { exists() }
            jsonPath("$.instance") { value("/error/conflict") }
        }
    }

    /** 验证未登记的业务错误码默认渲染为 400 */
    @Test
    fun `unmapped business code defaults to bad request`() {
        mockMvc.get("/error/unmapped").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("FIXTURE.INSUFFICIENT_BALANCE") }
            jsonPath("$.title") { value("账户余额不足") }
            jsonPath("$.detail") { value("账户余额不足") }
        }
    }

    /** 验证兜底异常渲染安全消息且不泄露内部细节 */
    @Test
    fun `unexpected exception hides internals`() {
        val result = mockMvc.get("/error/crash").andReturn()

        assertEquals(500, result.response.status)
        val body = result.response.contentAsString
        assertTrue(body.contains("\"code\":\"COMMON.INTERNAL_ERROR\""), body)
        assertTrue(body.contains("\"detail\":\"服务内部错误\""), body)
        assertFalse(body.contains("hunter2"), "兜底响应不得泄露原始异常消息")
        assertFalse(body.contains("10.0.0.1"), "兜底响应不得泄露内部地址")
    }

    /** 验证未匹配路由渲染为 404 Problem Details */
    @Test
    fun `unknown path renders not found problem`() {
        mockMvc.get("/definitely-missing").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("COMMON.RESOURCE_NOT_FOUND") }
            jsonPath("$.traceId") { exists() }
        }
    }

    /** 验证 @Valid 请求体校验失败聚合为 400 INVALID_ARGUMENT */
    @Test
    fun `body validation failure renders invalid argument`() {
        mockMvc.post("/error/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":""}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("COMMON.INVALID_ARGUMENT") }
            jsonPath("$.detail") { value("上报编码不能为空") }
        }
    }
}

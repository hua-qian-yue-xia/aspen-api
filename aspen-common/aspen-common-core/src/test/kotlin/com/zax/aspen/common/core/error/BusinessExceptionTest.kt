package com.zax.aspen.common.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 验证业务异常只公开稳定且安全的错误信息 */
class BusinessExceptionTest {
    /** 验证错误契约, 安全详情和原始异常均被正确保留 */
    @Test
    fun `uses the stable error contract and safe detail`() {
        val cause = IllegalStateException("数据库内部诊断信息")
        val exception = BusinessException(
            errorCode = CommonErrorCode.STATE_CONFLICT,
            detail = "资源已发生变更",
            cause = cause,
        )

        assertEquals("COMMON.STATE_CONFLICT", exception.errorCode.code)
        assertEquals("资源已发生变更", exception.message)
        assertSame(cause, exception.cause)
    }

    /** 验证全部公共错误的默认对外消息使用中文 */
    @Test
    fun `uses Chinese default messages for every common error`() {
        val expectedMessages = mapOf(
            CommonErrorCode.INVALID_ARGUMENT to "请求参数不正确",
            CommonErrorCode.METHOD_NOT_ALLOWED to "请求方法不支持",
            CommonErrorCode.UNSUPPORTED_MEDIA_TYPE to "请求的媒体类型不支持",
            CommonErrorCode.RESOURCE_NOT_FOUND to "请求的资源不存在",
            CommonErrorCode.UNAUTHORIZED to "未认证或凭据已失效",
            CommonErrorCode.FORBIDDEN to "无权访问目标资源",
            CommonErrorCode.TOO_MANY_REQUESTS to "请求过于频繁",
            CommonErrorCode.STATE_CONFLICT to "资源状态不允许执行当前操作",
            CommonErrorCode.DEPENDENCY_UNAVAILABLE to "依赖服务暂时不可用",
            CommonErrorCode.INTERNAL_ERROR to "服务内部错误",
        )

        assertEquals(expectedMessages, CommonErrorCode.entries.associateWith { it.defaultMessage })
    }
}

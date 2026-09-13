package com.zax.aspen.common.web.error

import com.zax.aspen.common.core.error.CommonErrorCode
import com.zax.aspen.common.core.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * ErrorCode 到 HTTP 状态码的映射
 *
 * core 刻意不携带 HTTP 语义 (`ErrorCode` 可被非 Web 消费方引用, 其 KDoc 已声明
 * 状态映射归 Web 公共模块), 本类是唯一的映射落点: 公共错误码精确映射,
 * 未登记的业务错误码默认 400 (调用方语义被服务规则拒绝); 某业务错误码需要
 * 精确状态码 (如 404/409) 时在此登记, 禁止在服务内各自散落映射
 */
class AspenErrorCodeStatusMapper {
    /** 以稳定机器码为 key 的公共错误码映射表 */
    private val statusByCode: Map<String, HttpStatus> = mapOf(
        CommonErrorCode.INVALID_ARGUMENT.code to HttpStatus.BAD_REQUEST,
        CommonErrorCode.METHOD_NOT_ALLOWED.code to HttpStatus.METHOD_NOT_ALLOWED,
        CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.code to HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        CommonErrorCode.RESOURCE_NOT_FOUND.code to HttpStatus.NOT_FOUND,
        CommonErrorCode.UNAUTHORIZED.code to HttpStatus.UNAUTHORIZED,
        CommonErrorCode.FORBIDDEN.code to HttpStatus.FORBIDDEN,
        CommonErrorCode.TOO_MANY_REQUESTS.code to HttpStatus.TOO_MANY_REQUESTS,
        CommonErrorCode.STATE_CONFLICT.code to HttpStatus.CONFLICT,
        CommonErrorCode.DEPENDENCY_UNAVAILABLE.code to HttpStatus.SERVICE_UNAVAILABLE,
        CommonErrorCode.INTERNAL_ERROR.code to HttpStatus.INTERNAL_SERVER_ERROR,
    )

    /**
     * 查询错误码对应的 HTTP 状态
     *
     * @param errorCode 业务异常携带的错误码契约
     * @return 公共错误码返回精确映射; 未登记的业务错误码统一返回 400
     */
    fun statusFor(errorCode: ErrorCode): HttpStatus = statusByCode[errorCode.code] ?: HttpStatus.BAD_REQUEST
}

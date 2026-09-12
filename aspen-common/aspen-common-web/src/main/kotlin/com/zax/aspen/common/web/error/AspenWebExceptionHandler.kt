package com.zax.aspen.common.web.error

import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import com.zax.aspen.common.core.error.ErrorCode
import com.zax.aspen.common.web.trace.TraceId
import jakarta.validation.ConstraintViolationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

/**
 * 统一错误渲染为 RFC 9457 Problem Details
 *
 * 成功响应直接返回 DTO/VO, 失败响应经本类统一为 `application/problem+json`:
 * `title` 取 `ErrorCode.defaultMessage`, `detail` 取可安全外发的错误详情,
 * 扩展字段 `code` 为稳定机器错误码、`traceId` 为排查标识 (与响应头、MDC 一致)。
 * 协议级客户端错误 (方法不支持 405、媒体类型不支持 415、缺参/类型不匹配 400)
 * 与校验失败同属 4xx 契约, 必须精确渲染、不得落入兜底 500 污染告警语义;
 * 兜底异常的 `detail` 只给安全消息, 原始异常与堆栈只随日志 (携带 traceId) 落盘;
 * `type` 在错误文档站真实存在前保持默认 `about:blank`, 机器判别以 `code` 为准
 */
@RestControllerAdvice
class AspenWebExceptionHandler(
    private val statusMapper: AspenErrorCodeStatusMapper,
) {
    private val logger: Logger = LoggerFactory.getLogger(AspenWebExceptionHandler::class.java)

    /**
     * 渲染业务失败
     *
     * @param exception Service 层抛出的业务异常, detail 必须可安全返回给调用方
     * @return 状态取错误码映射的 Problem Details, 含 code 与 traceId 扩展字段
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(exception: BusinessException): ProblemDetail {
        val status = statusMapper.statusFor(exception.errorCode)
        logger.warn(
            "业务失败 code={} status={} detail={}",
            exception.errorCode.code,
            status.value(),
            exception.detail,
            exception,
        )
        return problem(status, exception.errorCode, exception.detail)
    }

    /**
     * 渲染 @Valid 请求体校验失败
     *
     * @param exception 请求体绑定与校验异常
     * @return 400 Problem Details, detail 聚合全部字段校验消息
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(exception: MethodArgumentNotValidException): ProblemDetail {
        val detail = exception.bindingResult.allErrors.joinToString("; ") { it.defaultMessage.orEmpty() }
            .ifBlank { CommonErrorCode.INVALID_ARGUMENT.defaultMessage }
        logger.warn("请求体校验失败 detail={}", detail)
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, detail)
    }

    /**
     * 渲染方法级参数校验失败
     *
     * @param exception 非请求体参数 (如 @RequestParam/@PathVariable) 的校验异常
     * @return 400 Problem Details, detail 聚合全部校验消息
     */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(exception: HandlerMethodValidationException): ProblemDetail {
        val detail = exception.allErrors.joinToString("; ") { it.defaultMessage.orEmpty() }
            .ifBlank { CommonErrorCode.INVALID_ARGUMENT.defaultMessage }
        logger.warn("方法参数校验失败 detail={}", detail)
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, detail)
    }

    /**
     * 渲染不可读请求体
     *
     * @param exception 反序列化失败的异常, 原始内容可能含敏感片段, 不外发
     * @return 400 Problem Details, detail 为固定安全消息
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ProblemDetail {
        logger.warn("请求体不可读", exception)
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, "请求体格式不正确")
    }

    /**
     * 渲染请求方法不支持
     *
     * @param exception HTTP 方法与接口全部映射不匹配的框架异常
     * @return 405 Problem Details, detail 指明被拒绝的方法, 属客户端协议错误不得落入兜底 500
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(exception: HttpRequestMethodNotSupportedException): ProblemDetail {
        logger.warn("请求方法不支持 method={}", exception.method)
        return problem(
            HttpStatus.METHOD_NOT_ALLOWED,
            CommonErrorCode.METHOD_NOT_ALLOWED,
            "请求方法 ${exception.method} 不被支持",
        )
    }

    /**
     * 渲染请求媒体类型不支持
     *
     * @param exception 请求 Content-Type 无可用消息转换器的框架异常
     * @return 415 Problem Details, detail 回显客户端自己发送的媒体类型, 属客户端协议错误
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(exception: HttpMediaTypeNotSupportedException): ProblemDetail {
        logger.warn("媒体类型不支持 contentType={}", exception.contentType)
        val detail = exception.contentType
            ?.let { "Content-Type '$it' 不被支持" }
            ?: CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, detail)
    }

    /**
     * 渲染缺少必填请求参数
     *
     * @param exception 必填 @RequestParam 缺失的绑定异常, 参数名是 API 契约可安全外发
     * @return 400 Problem Details, detail 指明缺失的参数名
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ProblemDetail {
        logger.warn("缺少请求参数 name={} type={}", exception.parameterName, exception.parameterType)
        return problem(
            HttpStatus.BAD_REQUEST,
            CommonErrorCode.INVALID_ARGUMENT,
            "缺少必填请求参数: ${exception.parameterName}",
        )
    }

    /**
     * 渲染请求参数类型不匹配
     *
     * @param exception 参数绑定类型转换失败的异常, 参数名是 API 契约可安全外发
     * @return 400 Problem Details, detail 指明类型不匹配的参数名
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ProblemDetail {
        logger.warn("参数类型不匹配 name={}", exception.name)
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, "请求参数 ${exception.name} 类型不正确")
    }

    /**
     * 渲染服务层方法级校验失败
     *
     * Spring 6.1 起 Controller 参数约束由 MVC 内建校验抛 HandlerMethodValidationException
     * (已由 [handleMethodValidation] 渲染), 本处理器承接非 Controller Bean (`@Validated`
     * 服务) 抛出的校验异常
     *
     * @param exception 方法级校验聚合的约束违反异常
     * @return 400 Problem Details, detail 聚合全部约束消息
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ProblemDetail {
        val detail = exception.constraintViolations.joinToString("; ") { it.message }
            .ifBlank { CommonErrorCode.INVALID_ARGUMENT.defaultMessage }
        logger.warn("方法级校验失败 detail={}", detail)
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT, detail)
    }

    /**
     * 渲染未匹配路由
     *
     * @param exception 静态资源/路由未命中的异常
     * @return 404 Problem Details, detail 为固定安全消息
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(exception: NoResourceFoundException): ProblemDetail {
        logger.warn("路由未命中 method={} path={}", exception.httpMethod.name(), exception.resourcePath)
        return problem(HttpStatus.NOT_FOUND, CommonErrorCode.RESOURCE_NOT_FOUND, CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage)
    }

    /**
     * 兜底渲染未分类异常
     *
     * @param exception 未被上层捕获的任意异常, 消息可能含内部细节, 一律不外发
     * @return 500 Problem Details, detail 为固定安全消息, 完整堆栈只进日志
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ProblemDetail {
        logger.error("未分类异常", exception)
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR,
            CommonErrorCode.INTERNAL_ERROR.defaultMessage,
        )
    }

    /**
     * 组装 Problem Details 响应体
     *
     * @param status HTTP 状态码
     * @param errorCode 稳定机器错误码契约
     * @param detail 可安全返回给调用方的错误详情
     * @return 含 code 与 traceId 扩展字段的 Problem Details
     */
    private fun problem(status: HttpStatus, errorCode: ErrorCode, detail: String): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.title = errorCode.defaultMessage
        problem.setProperty("code", errorCode.code)
        // 正常链路 traceId 已由 AspenTraceIdFilter 写入 MDC; 兜底生成保证字段恒存在
        problem.setProperty(TraceId.MDC_KEY, MDC.get(TraceId.MDC_KEY) ?: TraceId.generate())
        runCatching {
            val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
            request?.requestURI?.let { problem.instance = URI.create(it) }
        }
        return problem
    }
}

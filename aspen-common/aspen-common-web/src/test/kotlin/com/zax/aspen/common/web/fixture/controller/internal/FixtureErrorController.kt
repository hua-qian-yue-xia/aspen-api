package com.zax.aspen.common.web.fixture.controller.internal

import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import com.zax.aspen.common.web.fixture.FixtureBalanceErrorCode
import com.zax.aspen.common.web.fixture.FixtureLookupService
import com.zax.aspen.common.web.fixture.FixtureReportRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 统一错误契约测试的样例 Controller
 *
 * 位于 internal 受众包、不加受众前缀; 每个端点固定抛出一类异常,
 * 供 MockMvc 断言 AspenWebExceptionHandler 的渲染结果
 */
@RestController
class FixtureErrorController(
    private val lookupService: FixtureLookupService,
) {
    /** 抛出映射了精确状态 (409) 的公共错误码 */
    @GetMapping("/error/conflict")
    fun `conflict endpoint`(): Nothing = throw BusinessException(CommonErrorCode.STATE_CONFLICT, "路由已停用, 拒绝修改")

    /** 抛出未登记进公共状态映射的业务错误码, 断言默认 400 */
    @GetMapping("/error/unmapped")
    fun `unmapped endpoint`(): Nothing = throw BusinessException(FixtureBalanceErrorCode, "账户余额不足")

    /** 抛出映射了 503 的公共错误码, 断言高可用语义档位 */
    @GetMapping("/error/dependency")
    fun `dependency endpoint`(): Nothing = throw BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "路由存储暂时不可用")

    /** 抛出携带内部敏感信息的未分类异常, 断言响应不泄露 */
    @GetMapping("/error/crash")
    fun `crash endpoint`(): Nothing = throw IllegalStateException("jdbc:mysql://10.0.0.1:3306 password=hunter2")

    /** 触发 @Valid 请求体校验失败 */
    @PostMapping("/error/validate")
    fun `validate endpoint`(
        @Valid @RequestBody request: FixtureReportRequest,
    ): String = request.code

    /** 必填参数缺失触发 MissingServletRequestParameterException, 非数字触发类型不匹配 */
    @GetMapping("/error/param")
    fun `param endpoint`(
        @RequestParam page: Long,
    ): String = page.toString()

    /** 经样例服务触发服务层 @Validated 方法级校验失败 (ConstraintViolationException) */
    @GetMapping("/error/constraint")
    fun `constraint endpoint`(
        @RequestParam code: String,
    ): String = lookupService.lookup(code)
}

package com.zax.aspen.common.web.fixture

import jakarta.validation.constraints.NotBlank

/** 校验失败路径的样例请求体 */
data class FixtureReportRequest(
    /** 上报编码, 非空约束用于触发请求体校验异常 */
    @field:NotBlank(message = "上报编码不能为空")
    val code: String,
)

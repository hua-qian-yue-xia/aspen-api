package com.zax.aspen.admin.api.dto.sys

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 字典显示属性修改入参
 *
 * 只携带允许修改的显示属性 (显示名与分组), 编码不在修改面内; 内置字典同样只允许
 * 调整这两项, 启停走独立端点并受内置保护约束
 */
data class SysDictUpdateDTO(
    /** 字典显示名 */
    @field:NotBlank
    @field:Size(max = 100)
    val dictName: String,
    /** 字典分组, 小写下划线格式 */
    @field:NotBlank
    @field:Pattern(regexp = "[a-z][a-z0-9_]{0,63}")
    val dictGroup: String,
)

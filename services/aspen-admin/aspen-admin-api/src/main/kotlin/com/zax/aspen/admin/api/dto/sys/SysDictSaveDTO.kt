package com.zax.aspen.admin.api.dto.sys

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 字典新增入参
 *
 * 管理端写入模型; Bean Validation 只兜字段形状, 编码全局唯一 (含逻辑删除行占用) 与
 * 内置保护规则由 Admin Service 校验; 手工创建的字典固定为非内置, 启用状态
 */
data class SysDictSaveDTO(
    /** 字典编码, 小写下划线格式, 全局唯一且永久占用, 创建后不可修改 */
    @field:NotBlank
    @field:Pattern(regexp = "[a-z][a-z0-9_]{1,63}")
    val dictCode: String,
    /** 字典显示名, 用于管理界面展示与搜索 */
    @field:NotBlank
    @field:Size(max = 100)
    val dictName: String,
    /** 字典分组, 管理界面按域筛选字典, 小写下划线格式 */
    @field:NotBlank
    @field:Pattern(regexp = "[a-z][a-z0-9_]{0,63}")
    val dictGroup: String,
)

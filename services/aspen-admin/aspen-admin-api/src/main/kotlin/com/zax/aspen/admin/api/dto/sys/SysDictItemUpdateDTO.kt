package com.zax.aspen.admin.api.dto.sys

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 字典项展示属性修改入参
 *
 * 只携带允许修改的展示属性; 值、所属字典与父项不在修改面内 (改值需新建项并废弃
 * 旧项, 子树移动属二期), 内置字典项同样允许调整展示属性; isDefault 置位时由
 * Service 清除同字典其他默认项
 */
data class SysDictItemUpdateDTO(
    /** 字典项显示文本 */
    @field:NotBlank
    @field:Size(max = 100)
    val itemLabel: String,
    /** 表单默认选中项 */
    val isDefault: Boolean = false,
    /** 前端标签颜色, EnumColor 调色板令牌或 #RRGGBB 色值 */
    @field:Size(max = 32)
    val color: String? = null,
    /** 前端自定义样式类名 */
    @field:Size(max = 100)
    val cssClass: String? = null,
    /** 同字典内的展示顺序, 升序排列 */
    @field:Min(0)
    @field:Max(9999)
    val sortOrder: Int = 0,
)

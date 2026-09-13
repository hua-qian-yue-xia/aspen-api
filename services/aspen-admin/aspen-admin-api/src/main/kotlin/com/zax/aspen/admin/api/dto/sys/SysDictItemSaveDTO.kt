package com.zax.aspen.admin.api.dto.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 字典项新增入参
 *
 * 管理端写入模型; 所属字典存在且非内置、同字典内值唯一、父项属于同一字典由
 * Admin Service 校验
 */
data class SysDictItemSaveDTO(
    /** 所属字典主键 id */
    @field:NotNull
    val dictId: Long,
    /** 父字典项主键 id, 树形字典必填且必须属于同一字典, 扁平字典为空 */
    val parentId: Long? = null,
    /** 字典项显示文本, 直接用于下拉选项与翻译结果 */
    @field:NotBlank
    @field:Size(max = 100)
    val itemLabel: String,
    /** 字典项存储值, 业务表保存的就是该值; 同字典内唯一, 创建后不可修改 */
    @field:NotBlank
    @field:Size(max = 100)
    val itemValue: String,
    /** 表单默认选中项; 置位时 Service 清除同字典其他默认项 */
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
    /** 字典项启停状态 */
    val status: EnabledStatus = EnabledStatus.ENABLED,
)

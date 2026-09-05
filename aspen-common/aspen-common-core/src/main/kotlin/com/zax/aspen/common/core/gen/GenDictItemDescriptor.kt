package com.zax.aspen.common.core.gen

/**
 * 枚举字典项的扫描产物, 描述一个枚举值在字典中的镜像行
 *
 * 由 common-gen 从枚举常量生成: itemValue 取枚举 code, itemLabel 取 description,
 * color 取枚举 color, sortOrder 取声明顺序; 也作为 internal 上报契约的一部分,
 * 构造时校验取值, 保证远端输入在进入播种前即被拒绝
 */
data class GenDictItemDescriptor(
    /** 字典项存储值, 来源于枚举 code, 同字典内唯一 */
    val itemValue: String,
    /** 字典项显示文本, 来源于枚举 description */
    val itemLabel: String,
    /** 标签颜色, 来源于枚举 color, 取 EnumColor 令牌或 #RRGGBB; 无着色需求时为 null */
    val color: String?,
    /** 展示顺序, 来源于枚举声明顺序 */
    val sortOrder: Int,
) {
    init {
        require(itemValue.isNotBlank()) { "字典项存储值不得为空" }
        require(itemLabel.isNotBlank()) { "字典项显示文本不得为空: $itemValue" }
        require(color == null || com.zax.aspen.common.core.enums.EnumColor.isValid(color)) {
            "字典项颜色非法: $color"
        }
        require(sortOrder >= 0) { "字典项展示顺序不得为负: $itemValue" }
    }
}

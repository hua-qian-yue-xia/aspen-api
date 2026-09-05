package com.zax.aspen.common.core.enums.common

import com.zax.aspen.common.core.enums.AspenEnum

/**
 * 性别枚举
 *
 * 取值语义对齐 ISO/IEC 5218 性别表示标准(0 未知/1 男/2 女/9 不适用),
 * 存储采用小写字符串以与全库列值风格统一; not_applicable 用于企业账户等
 * 性别不适用的主体; 性别展示不着色
 */
enum class Gender(
    override val code: String,
    override val description: String,
) : AspenEnum {
    /** 未知性别, 对应 ISO 5218 的 0; 用户未填写或来源数据缺失时的默认值 */
    UNKNOWN("unknown", "未知"),

    /** 男性, 对应 ISO 5218 的 1 */
    MALE("male", "男"),

    /** 女性, 对应 ISO 5218 的 2 */
    FEMALE("female", "女"),

    /** 不适用, 对应 ISO 5218 的 9; 企业或组织账户等无性别主体 */
    NOT_APPLICABLE("not_applicable", "不适用"),
    ;

    override val color: String? = null
}

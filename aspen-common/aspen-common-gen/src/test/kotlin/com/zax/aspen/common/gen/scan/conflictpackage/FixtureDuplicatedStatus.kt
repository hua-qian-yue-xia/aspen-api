package com.zax.aspen.common.gen.scan.conflictpackage

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.gen.GenDict

/** 与 FixtureOrderStatus 编码冲突的枚举, 用于验证重复编码被拒绝 */
@GenDict(code = "fixture_order_status", name = "重复编码", group = "fixture")
enum class FixtureDuplicatedStatus(
    override val code: String,
    override val description: String,
) : AspenEnum {
    A("a", "甲"),
    ;

    override val color: String? = null
}

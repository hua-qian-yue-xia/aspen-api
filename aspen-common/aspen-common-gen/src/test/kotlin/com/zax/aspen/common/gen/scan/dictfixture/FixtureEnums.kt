package com.zax.aspen.common.gen.scan.dictfixture

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/** 扫描测试用的已声明字典枚举 */
@GenDict(code = "fixture_order_status", name = "订单状态", group = "fixture")
enum class FixtureOrderStatus(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    PAID("paid", "已支付", EnumColor.SUCCESS),
    SHIPPED("shipped", "已发货", null),
    CLOSED("closed", "已关闭", EnumColor.DANGER),
}

/** 扫描测试用的未声明字典枚举, 不应出现在目录中 */
enum class FixturePlainStatus(
    override val code: String,
    override val description: String,
) : AspenEnum {
    ON("on", "开"),
    OFF("off", "关"),
    ;

    override val color: String? = null
}

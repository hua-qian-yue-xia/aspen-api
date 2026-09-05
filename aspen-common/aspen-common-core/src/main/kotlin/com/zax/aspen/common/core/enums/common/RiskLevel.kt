package com.zax.aspen.common.core.enums.common

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 风险等级
 *
 * 分级参考 CVSS 严重度等级的事实标准, 用于权限点、操作审计与告警的统一风险表达;
 * high 及以上等级触发二次确认、审批或强制审计等加强控制, 具体控制动作由各使用方定义
 */
@GenDict(code = "risk_level", name = "风险等级", group = "common")
enum class RiskLevel(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 低风险, 只读或不影响他人数据的操作, 无附加控制 */
    LOW("low", "低风险", EnumColor.DEFAULT),

    /** 一般风险, 常规写操作, 默认等级 */
    NORMAL("normal", "一般风险", EnumColor.PRIMARY),

    /** 高风险, 影响他人数据或权限的操作, 需要二次确认或强制审计 */
    HIGH("high", "高风险", EnumColor.WARNING),

    /** 严重风险, 不可逆或影响全局的操作, 需要审批并强制审计 */
    CRITICAL("critical", "严重风险", EnumColor.DANGER),
}

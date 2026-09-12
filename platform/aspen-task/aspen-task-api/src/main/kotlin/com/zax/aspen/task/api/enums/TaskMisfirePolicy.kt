package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 任务错过触发 (Misfire) 策略
 *
 * Trigger 到点后超过 misfireThreshold 仍未被任何集群实例执行时的处置方式;
 * 每个任务必须显式选择, 禁止依赖 Quartz 未记录的默认行为 (技术架构 14.1.3)
 */
@GenDict(code = "task_misfire_policy", name = "错过触发策略", group = "task")
enum class TaskMisfirePolicy(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 补执行一次: 立即补偿执行错过的触发, 之后回归正常时点 */
    FIRE_ONCE("fire_once", "补执行一次", EnumColor.WARNING),

    /** 跳过已错过时点: 不补偿, 直接按下一个计划时点继续 */
    SKIP("skip", "跳过错过时点", EnumColor.DEFAULT),
}

package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 逻辑执行的触发来源
 *
 * 计划触发由 Quartz Trigger 到点产生; 人工触发经管理端立即执行, 请求携带
 * requestId 幂等键; 失败重试沿用原 executionId 与原来源, 不产生新来源
 */
@GenDict(code = "task_trigger_source", name = "任务触发来源", group = "task")
enum class TaskTriggerSource(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 计划触发: Quartz 按 cron/间隔/时点计算的正常触发 */
    SCHEDULED("scheduled", "计划触发", EnumColor.PRIMARY),

    /** 人工触发: 管理端手动执行一次, 与计划触发走完全相同的投递链路 */
    MANUAL("manual", "人工触发", EnumColor.GOLD),
}

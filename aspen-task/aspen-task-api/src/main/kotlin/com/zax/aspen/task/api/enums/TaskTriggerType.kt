package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 任务触发类型
 *
 * 决定任务到点的计算方式与 Quartz Trigger 形态; 三种类型都是 Quartz 原生能力,
 * 不引入自建轮询; 触发字段 (cron/间隔/时点) 的存在性由 Task Service 按本枚举校验
 */
@GenDict(code = "task_trigger_type", name = "任务触发类型", group = "task")
enum class TaskTriggerType(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** CRON 表达式触发, 必须显式指定 IANA 时区, 支持 6/7 位 Quartz 制式 */
    CRON("cron", "CRON 表达式", EnumColor.PRIMARY),

    /** 固定间隔触发, 从启用时刻起按 interval_seconds 循环 */
    FIXED_INTERVAL("fixed_interval", "固定间隔", EnumColor.BLUE),

    /** 一次性指定时点触发, 触发后 Trigger 自然结束, 任务不自动停用 */
    ONE_TIME("one_time", "一次性", EnumColor.GEEKBLUE),
}

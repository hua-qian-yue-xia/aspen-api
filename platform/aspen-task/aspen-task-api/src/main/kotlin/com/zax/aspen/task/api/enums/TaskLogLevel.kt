package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 执行过程日志级别
 *
 * 目标服务回传执行日志时的粗粒度分级, 供管理端过滤与着色; 语义对齐常见日志
 * 三级 (信息/警告/错误), 不引入调试级等高频级别, 防止日志表被刷爆
 */
@GenDict(code = "task_log_level", name = "任务日志级别", group = "task")
enum class TaskLogLevel(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 信息: 正常步骤推进与进度上报 */
    INFO("info", "信息", EnumColor.DEFAULT),

    /** 警告: 可继续执行的异常状况, 需要关注 */
    WARN("warn", "警告", EnumColor.WARNING),

    /** 错误: 步骤失败, 通常伴随本次投递最终失败 */
    ERROR("error", "错误", EnumColor.DANGER),
}

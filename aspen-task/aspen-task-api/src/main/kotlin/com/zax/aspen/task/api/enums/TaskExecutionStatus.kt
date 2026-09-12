package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 逻辑执行状态
 *
 * 每租户每逻辑执行一条状态: RUNNING 起始, 以 SUCCESS/FAILED 终态;
 * SKIPPED 表示并发策略跳过本轮 (未实际投递); 状态迁移受乐观锁保护
 */
@GenDict(code = "task_execution_status", name = "任务执行状态", group = "task")
enum class TaskExecutionStatus(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 执行中: 执行行已创建且 HTTP 投递尚未取得终态 */
    RUNNING("running", "执行中", EnumColor.PRIMARY),

    /** 成功: 目标返回 2xx 响应 */
    SUCCESS("success", "成功", EnumColor.SUCCESS),

    /** 失败: 超时/连接失败/非 2xx 响应/目标校验拒绝/重试耗尽等, 细分见 TaskFailureKind */
    FAILED("failed", "失败", EnumColor.DANGER),

    /** 跳过: 并发策略判定本轮不执行, 未发生投递 */
    SKIPPED("skipped", "跳过", EnumColor.DEFAULT),
}

package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 任务并发策略
 *
 * 同一任务上一轮执行仍在 RUNNING 时, 新触发到达的处置方式; 集群层面 Quartz
 * 只保证单个 Trigger 不重复抢占, 跨 Trigger 的并发控制由 Task 分发层按本策略执行
 */
@GenDict(code = "task_concurrent_policy", name = "任务并发策略", group = "task")
enum class TaskConcurrentPolicy(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 允许并发: 新触发照常执行, 适合目标服务自身保证幂等与短耗时的任务 */
    ALLOW("allow", "允许并发", EnumColor.SUCCESS),

    /** 运行中跳过: 存在 RUNNING 执行时本轮逐租户记 SKIPPED 后返回, 防止执行堆积 */
    SKIP("skip", "运行中跳过", EnumColor.WARNING),
}

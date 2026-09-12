package com.zax.aspen.task.api.constant

/**
 * 任务投递携带的 Aspen 溯源请求头常量
 *
 * 投递器对每个目标请求附加本组请求头 (在任务自定义 headers 之外), 内部微服务与
 * 外部项目统一携带; 目标服务以 EXECUTION_ID 幂等、按 ATTEMPT 识别同一逻辑执行的
 * 合法重试 (技术架构 14.1.3); 租户头 X-Aspen-Tenant-Id 的常量收敛于 common-core,
 * 由任务投递器与目标服务共同引用
 */
object TaskHttpHeaders {
    /** 任务定义主键头, 值为 task_definition 的 definition_id */
    const val TASK_ID = "X-Aspen-Task-Id"

    /** 逻辑执行唯一标识头, 同一逻辑执行重试时值不变, 目标服务以其幂等 */
    const val EXECUTION_ID = "X-Aspen-Execution-Id"

    /** 尝试轮次头, 首次投递为 1, 同一逻辑执行的重试递增 */
    const val ATTEMPT = "X-Aspen-Attempt"

    /** 计划触发时间头, ISO-8601 带时区偏移的文本形式 */
    const val FIRE_TIME = "X-Aspen-Fire-Time"
}

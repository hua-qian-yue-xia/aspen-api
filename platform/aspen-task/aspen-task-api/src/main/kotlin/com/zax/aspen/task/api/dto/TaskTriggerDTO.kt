package com.zax.aspen.task.api.dto

import jakarta.validation.constraints.Pattern
import java.util.UUID

/**
 * 任务人工触发入参
 *
 * 与计划触发走完全相同的投递链路, 便于在准生产环境演练任务; requestId 是幂等键,
 * 同一 requestId 的人工触发只会创建一次逻辑执行 (每次触发生成「每租户一条」),
 * 缺省时由服务端生成 UUID
 */
data class TaskTriggerDTO(
    /** 人工触发幂等键, 字母数字与 ._- 组成, 客户端重放同一请求时避免重复创建逻辑执行 */
    @field:Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
    val requestId: String = UUID.randomUUID().toString(),
)

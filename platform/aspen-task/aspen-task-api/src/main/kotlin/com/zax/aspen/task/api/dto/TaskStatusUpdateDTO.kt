package com.zax.aspen.task.api.dto

import com.zax.aspen.common.core.enums.common.EnabledStatus
import jakarta.validation.constraints.NotNull

/**
 * 任务启停状态修改入参
 *
 * 启用经 Quartz resume/补建 Trigger 后落库, 停用经 pause 后落库;
 * 同步失败不向调用方报告成功 (技术架构 14.1.3)
 */
data class TaskStatusUpdateDTO(
    /** 目标启停状态 */
    @field:NotNull
    val status: EnabledStatus,
)

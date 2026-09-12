package com.zax.aspen.task.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 执行过程日志的批量上报入参
 *
 * 一次上报同一逻辑执行的多条日志; 目标服务从投递请求的溯源请求头
 * (X-Aspen-Execution-Id) 取得 executionId, 执行到哪一步就报到哪一步
 */
data class TaskExecutionLogReportDTO(
    /** 目标逻辑执行唯一标识 */
    @field:NotBlank
    @field:Size(max = 128)
    val executionId: String,
    /** 本批日志条目, 至少一条 */
    @field:NotNull
    @field:Size(min = 1, max = 100)
    val logs: List<TaskExecutionLogEntryDTO>,
)

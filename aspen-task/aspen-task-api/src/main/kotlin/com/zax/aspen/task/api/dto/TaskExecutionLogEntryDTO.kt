package com.zax.aspen.task.api.dto

import com.zax.aspen.task.api.enums.TaskLogLevel
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 执行过程日志的单条上报入参
 *
 * seq 是调用方维护的自增序号 (从 1 起): (executionId, seq) 唯一约束使重复上报
 * 幂等跳过、乱序到达不破坏排序; loggedAt 缺省时由平台按接收时间补齐
 */
data class TaskExecutionLogEntryDTO(
    /** 本条日志在所属执行内的自增序号, 从 1 起, 调用方保证单调 */
    @field:NotNull
    @field:Min(1)
    val seq: Int,
    /** 日志级别, 缺省按信息处理 */
    val level: TaskLogLevel = TaskLogLevel.INFO,
    /** 日志消息文本 */
    @field:NotBlank
    @field:Size(max = 2000)
    val message: String,
    /** 目标侧的日志产生时间; 缺省时由平台按接收时间补齐 */
    val loggedAt: LocalDateTime? = null,
)

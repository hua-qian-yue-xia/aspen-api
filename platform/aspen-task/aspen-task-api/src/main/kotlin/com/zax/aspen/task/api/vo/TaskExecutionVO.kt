package com.zax.aspen.task.api.vo

import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskFailureKind
import com.zax.aspen.task.api.enums.TaskTriggerSource
import java.time.LocalDateTime

/**
 * 任务执行记录的管理查询视图
 *
 * 每租户每逻辑执行一条; taskCode 是任务删除后仍可读的快照;
 * 失败类别与 HTTP 状态供管理端分类筛选, 响应片段截断存储
 */
data class TaskExecutionVO(
    /** 逻辑执行唯一标识, 同一逻辑执行重试时不变 */
    val executionId: String,
    /** 任务定义主键快照 */
    val definitionId: Long,
    /** 任务编码快照, 任务删除后仍可读 */
    val taskCode: String,
    /** 本次执行归属的租户标识 */
    val tenantId: Long,
    /** 触发来源 */
    val triggerSource: TaskTriggerSource,
    /** 人工触发幂等键, 计划触发为空 */
    val requestId: String?,
    /** 计划触发时间 */
    val fireTime: LocalDateTime,
    /** 尝试轮次, 首次为 1, 重试递增 */
    val attempt: Int,
    /** 执行状态 */
    val status: TaskExecutionStatus,
    /** 失败类别, 仅 FAILED 时非空 */
    val failureKind: TaskFailureKind?,
    /** 目标返回的 HTTP 状态码, 未取得响应时为空 */
    val httpStatus: Int?,
    /** 目标响应体片段, 截断存储 */
    val responseSnippet: String?,
    /** 失败原因文本 */
    val errorMessage: String?,
    /** 当前轮次投递开始时间 */
    val startedAt: LocalDateTime,
    /** 当前轮次投递结束时间, 未结束时为空 */
    val finishedAt: LocalDateTime?,
    /** 当前轮次耗时毫秒数 */
    val durationMs: Long?,
    /** 记录创建时间 */
    val createdAt: LocalDateTime,
    /** 记录最近更新时间 */
    val updatedAt: LocalDateTime,
)

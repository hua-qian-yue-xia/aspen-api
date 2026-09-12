package com.zax.aspen.task.api.dto

import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskTriggerSource
import jakarta.validation.constraints.Min
import java.time.LocalDateTime

/**
 * 任务执行记录分页查询入参
 *
 * 支持按任务、租户、状态、来源与计划触发时间区间过滤; 查询条件全部可空
 */
data class TaskExecutionPageQuery(
    /** 从 1 开始的目标页码 */
    @field:Min(1)
    val pageNumber: Int = 1,
    /** 单页请求的数据条数 */
    @field:Min(1)
    val pageSize: Int = 20,
    /** 任务定义主键过滤 */
    val definitionId: Long? = null,
    /** 租户标识过滤 */
    val tenantId: Long? = null,
    /** 执行状态过滤 */
    val status: TaskExecutionStatus? = null,
    /** 触发来源过滤 */
    val triggerSource: TaskTriggerSource? = null,
    /** 计划触发时间下界 (含) */
    val fireTimeFrom: LocalDateTime? = null,
    /** 计划触发时间上界 (含) */
    val fireTimeTo: LocalDateTime? = null,
)

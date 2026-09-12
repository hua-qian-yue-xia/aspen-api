package com.zax.aspen.task.biz.service.task

import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.task.api.dto.TaskExecutionPageQuery
import com.zax.aspen.task.api.vo.TaskExecutionVO
import com.zax.aspen.task.biz.entity.TaskExecutionEntity
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import org.springframework.stereotype.Service

/**
 * 执行记录的查询与手动重派
 *
 * 查询面向管理端排障: 按任务、租户、状态、来源与触发时间区间过滤;
 * 手动重派委托投递处理器, 沿用原 executionId 追加一轮投递
 */
@Service
class TaskExecutionService(
    private val taskExecutionRepository: TaskExecutionRepository,
    private val dispatchHandler: TaskDispatchHandler,
    private val databaseLimits: DatabaseLimits,
) {
    /**
     * 分页查询执行记录
     *
     * @param query 分页与过滤条件
     * @return 执行记录视图分页结果
     */
    fun pageExecutions(query: TaskExecutionPageQuery): PageResult<TaskExecutionVO> {
        val pageSize = databaseLimits.requirePageSize(query.pageSize)
        val page = taskExecutionRepository.page(query, pageSize)
        return PageResult(
            items = page.items.map { it.toView() },
            totalElements = page.totalElements,
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
        )
    }

    /**
     * 手动重派失败的逻辑执行
     *
     * @param executionId 逻辑执行唯一标识, 仅 FAILED 状态可重派
     */
    fun retryExecution(executionId: String) {
        dispatchHandler.retryNow(executionId)
    }

    /**
     * 实体转管理视图
     *
     * @return 覆盖执行记录全部业务列与审计列的视图
     */
    private fun TaskExecutionEntity.toView(): TaskExecutionVO =
        TaskExecutionVO(
            executionId = executionId,
            definitionId = definitionId,
            taskCode = taskCode,
            tenantId = tenantId,
            triggerSource = triggerSource,
            requestId = requestId,
            fireTime = fireTime,
            attempt = attempt,
            status = status,
            failureKind = failureKind,
            httpStatus = httpStatus,
            responseSnippet = responseSnippet,
            errorMessage = errorMessage,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = durationMs,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

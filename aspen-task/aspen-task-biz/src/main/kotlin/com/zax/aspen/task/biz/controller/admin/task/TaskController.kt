package com.zax.aspen.task.biz.controller.admin.task

import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.task.api.contract.TaskApi
import com.zax.aspen.task.api.dto.TaskExecutionPageQuery
import com.zax.aspen.task.api.dto.TaskPageQuery
import com.zax.aspen.task.api.dto.TaskSaveDTO
import com.zax.aspen.task.api.dto.TaskStatusUpdateDTO
import com.zax.aspen.task.api.dto.TaskTriggerDTO
import com.zax.aspen.task.api.vo.TaskExecutionLogVO
import com.zax.aspen.task.api.vo.TaskExecutionVO
import com.zax.aspen.task.api.vo.TaskVO
import com.zax.aspen.task.biz.service.task.TaskDefinitionService
import com.zax.aspen.task.biz.service.task.TaskExecutionLogService
import com.zax.aspen.task.biz.service.task.TaskExecutionService
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * 统一任务服务的管理端点
 *
 * 路径与映射继承 task-api 的 TaskApi; 位于 controller/admin 受众包, 经
 * aspen-common-web 自动携带 /admin-api 前缀, 网关按 /admin-api/task 路径前缀
 * 路由到本服务; v1 无鉴权, 由 Gateway 统一入口兜底, RBAC 就绪后收紧
 */
@RestController
class TaskController(
    private val taskDefinitionService: TaskDefinitionService,
    private val taskExecutionService: TaskExecutionService,
    private val taskExecutionLogService: TaskExecutionLogService,
) : TaskApi {
    override fun createTask(command: TaskSaveDTO): TaskVO = taskDefinitionService.createTask(command)

    override fun updateTask(definitionId: Long, command: TaskSaveDTO): TaskVO =
        taskDefinitionService.updateTask(definitionId, command)

    override fun updateTaskStatus(definitionId: Long, command: TaskStatusUpdateDTO): TaskVO =
        taskDefinitionService.updateTaskStatus(definitionId, command.status)

    override fun deleteTask(definitionId: Long) = taskDefinitionService.deleteTask(definitionId)

    override fun triggerTask(definitionId: Long, command: TaskTriggerDTO?) =
        taskDefinitionService.triggerTask(definitionId, command)

    override fun getTask(definitionId: Long): TaskVO = taskDefinitionService.getTask(definitionId)

    override fun pageTasks(query: TaskPageQuery): PageResult<TaskVO> = taskDefinitionService.pageTasks(query)

    override fun nextFireTimes(definitionId: Long, count: Int): List<LocalDateTime> =
        taskDefinitionService.nextFireTimes(definitionId, count)

    override fun pageExecutions(query: TaskExecutionPageQuery): PageResult<TaskExecutionVO> =
        taskExecutionService.pageExecutions(query)

    override fun retryExecution(executionId: String) = taskExecutionService.retryExecution(executionId)

    override fun executionLogs(executionId: String): List<TaskExecutionLogVO> =
        taskExecutionLogService.executionLogs(executionId)
}

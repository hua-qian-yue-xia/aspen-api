package com.zax.aspen.task.api.contract

import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.task.api.dto.TaskExecutionPageQuery
import com.zax.aspen.task.api.dto.TaskPageQuery
import com.zax.aspen.task.api.dto.TaskSaveDTO
import com.zax.aspen.task.api.dto.TaskStatusUpdateDTO
import com.zax.aspen.task.api.dto.TaskTriggerDTO
import com.zax.aspen.task.api.vo.TaskExecutionLogVO
import com.zax.aspen.task.api.vo.TaskExecutionVO
import com.zax.aspen.task.api.vo.TaskVO
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

/**
 * 统一任务服务的管理契约
 *
 * 由 aspen-task-biz 的 controller/admin/task 实现, 经 aspen-common-web 自动携带
 * /admin-api 受众前缀, 网关按 /admin-api/task 路径前缀路由到本服务; 所有写操作
 * 「先同步 Quartz、后落定义表」, 调度同步失败不报告成功; v1 无鉴权, 由 Gateway
 * 统一入口兜底, RBAC 就绪后收紧
 */
interface TaskApi {
    /**
     * 新增任务; 编码重复拒绝, 成功后任务按启停状态进入调度
     *
     * @param command 任务新增入参, taskCode 全局唯一且永久占用
     * @return 已落库的任务视图, 含生成的 id 与版本号
     */
    @PostMapping(PATH)
    fun createTask(
        @RequestBody @Valid command: TaskSaveDTO,
    ): TaskVO

    /**
     * 修改任务; 编码不可变更, 触发规则变更即时重排 Quartz Trigger
     *
     * @param definitionId 目标任务的主键 id, 不存在时拒绝
     * @param command 任务修改入参, taskCode 必须与既有值一致
     * @return 修改并重查全行后的任务视图
     */
    @PutMapping("$PATH/{definitionId}")
    fun updateTask(
        @PathVariable("definitionId") definitionId: Long,
        @RequestBody @Valid command: TaskSaveDTO,
    ): TaskVO

    /**
     * 修改任务启停状态; 启用补建/恢复 Trigger, 停用暂停触发
     *
     * @param definitionId 目标任务的主键 id, 不存在时拒绝
     * @param command 目标启停状态
     * @return 状态变更后的任务视图
     */
    @PutMapping("$PATH/{definitionId}/status")
    fun updateTaskStatus(
        @PathVariable("definitionId") definitionId: Long,
        @RequestBody @Valid command: TaskStatusUpdateDTO,
    ): TaskVO

    /**
     * 逻辑删除任务, 同步清理 Quartz Job/Trigger 与指定租户清单, 执行记录保留审计
     *
     * @param definitionId 目标任务的主键 id, 不存在时拒绝
     */
    @DeleteMapping("$PATH/{definitionId}")
    fun deleteTask(
        @PathVariable("definitionId") definitionId: Long,
    )

    /**
     * 人工触发一次任务; 与计划触发走相同投递链路, requestId 幂等
     *
     * @param definitionId 目标任务的主键 id, 必须处于启用状态
     * @param command 触发入参, requestId 缺省时服务端生成
     */
    @PostMapping("$PATH/{definitionId}/trigger")
    fun triggerTask(
        @PathVariable("definitionId") definitionId: Long,
        @RequestBody(required = false) command: TaskTriggerDTO?,
    )

    /**
     * 查询任务详情, 含指定租户清单
     *
     * @param definitionId 目标任务的主键 id
     * @return 任务视图, 不存在或已逻辑删除时拒绝
     */
    @GetMapping("$PATH/{definitionId}")
    fun getTask(
        @PathVariable("definitionId") definitionId: Long,
    ): TaskVO

    /**
     * 分页查询任务定义
     *
     * @param query 分页与过滤条件, 条件全部可空
     * @return 任务视图分页结果
     */
    @GetMapping("$PATH/page")
    fun pageTasks(
        @Valid query: TaskPageQuery,
    ): PageResult<TaskVO>

    /**
     * 预览任务未来的触发时点, 供管理端在保存前校验触发规则
     *
     * @param definitionId 目标任务的主键 id
     * @param count 预览的时点数量, 1 到 10 之间
     * @return 未来触发时点列表, 按时间升序; 无未来触发时返回空列表
     */
    @GetMapping("$PATH/{definitionId}/next-times")
    fun nextFireTimes(
        @PathVariable("definitionId") definitionId: Long,
        @RequestParam("count") count: Int,
    ): List<LocalDateTime>

    /**
     * 分页查询执行记录
     *
     * @param query 分页与过滤条件, 支持按任务、租户、状态、来源与触发时间区间过滤
     * @return 执行记录视图分页结果
     */
    @GetMapping("$PATH/execution/page")
    fun pageExecutions(
        @Valid query: TaskExecutionPageQuery,
    ): PageResult<TaskExecutionVO>

    /**
     * 手动重派失败的逻辑执行; 沿用原 executionId, 尝试轮次加一立即投递
     *
     * @param executionId 逻辑执行唯一标识, 仅 FAILED 状态可重派
     */
    @PostMapping("$PATH/execution/{executionId}/retry")
    fun retryExecution(
        @PathVariable("executionId") executionId: String,
    )

    /**
     * 查询逻辑执行的过程日志, 按 seq 升序返回完整走向
     *
     * @param executionId 逻辑执行唯一标识
     * @return 过程日志列表, 无回传日志或执行已过保留期时返回空列表
     */
    @GetMapping("$PATH/execution/{executionId}/log")
    fun executionLogs(
        @PathVariable("executionId") executionId: String,
    ): List<TaskExecutionLogVO>

    companion object {
        /** 管理端点路径前缀, 受众前缀 /admin-api 由 Controller 包位置决定, 此处只写相对路径 */
        const val PATH = "/task"
    }
}

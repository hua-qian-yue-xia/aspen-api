package com.zax.aspen.task.biz.service.task

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.task.api.dto.TaskPageQuery
import com.zax.aspen.task.api.dto.TaskSaveDTO
import com.zax.aspen.task.api.dto.TaskTriggerDTO
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerType
import com.zax.aspen.task.api.vo.TaskVO
import com.zax.aspen.task.biz.dispatch.http.TaskHttpGuard
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.repository.task.TaskDefinitionRepository
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import com.zax.aspen.task.biz.repository.task.TaskTenantRepository
import com.zax.aspen.task.biz.scheduler.quartz.QuartzTaskSynchronizer
import com.zax.aspen.task.biz.scheduler.quartz.TaskTriggerRules
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDateTime

/**
 * 任务定义的生命周期管理
 *
 * 写路径统一「DB 落库 + Quartz 同步」双段: 同步失败抛出异常使 DB 事务回滚, 不向
 * 调用方报告启用成功; Quartz 使用自身连接独立提交, 两段之间的窗口期漂移由启动
 * 对账器闭环; 编码唯一性与触发字段语义在 Service 层强制
 */
@Service
class TaskDefinitionService(
    private val taskDefinitionRepository: TaskDefinitionRepository,
    private val taskTenantRepository: TaskTenantRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val synchronizer: QuartzTaskSynchronizer,
    private val taskHttpGuard: TaskHttpGuard,
    private val databaseLimits: DatabaseLimits,
) {
    /**
     * 查询任务详情, 含圈定租户清单
     *
     * @param definitionId 任务定义主键
     * @return 任务视图, 不存在或已逻辑删除时拒绝
     */
    fun getTask(definitionId: Long): TaskVO {
        val definition = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        return definition.toView(taskTenantRepository.findTenantIds(definitionId))
    }

    /**
     * 分页查询任务定义, 圈定租户清单按页批量装载
     *
     * @param query 分页与过滤条件
     * @return 任务视图分页结果
     */
    fun pageTasks(query: TaskPageQuery): PageResult<TaskVO> {
        val pageSize = databaseLimits.requirePageSize(query.pageSize)
        val page = taskDefinitionRepository.page(query, pageSize)
        val selectedIds = page.items.filter { it.tenantScope == TaskTenantScope.SELECTED_TENANTS }.map { it.definitionId }
        val tenantIdsByDefinition = taskTenantRepository.findByDefinitionIds(selectedIds).groupBy(
            keySelector = { it.definitionId },
            valueTransform = { it.tenantId },
        )
        return PageResult(
            items = page.items.map { it.toView(tenantIdsByDefinition[it.definitionId].orEmpty()) },
            totalElements = page.totalElements,
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
        )
    }

    /**
     * 新增任务; 编码重复拒绝, 落库后同步 Quartz, 同步失败整体回滚
     *
     * @param command 任务新增入参
     * @return 已落库的任务视图, 含生成的 id 与版本号
     */
    @Transactional
    fun createTask(command: TaskSaveDTO): TaskVO {
        validate(command)
        require(taskDefinitionRepository.findByCode(command.taskCode) == null) { "任务编码已存在: ${command.taskCode}" }
        val saved = try {
            taskDefinitionRepository.insert(command, ADMIN_API_IDENTITY)
        } catch (e: Exception) {
            if (e.isDuplicateKeyViolation()) {
                throw IllegalArgumentException("任务编码已存在或曾删除后保留, 不可复用: ${command.taskCode}", e)
            }
            throw e
        }
        // JobKey 依赖生成的主键, 创建只能先落库后同步; 同步失败抛出使事务回滚
        synchronizer.scheduleOrReplace(saved)
        replaceTenantSelection(saved.definitionId, command)
        return saved.toView(taskTenantRepository.findTenantIds(saved.definitionId))
    }

    /**
     * 修改任务; 编码不可变, 乐观锁冲突拒绝, 落库后重排 Quartz Trigger
     *
     * @param definitionId 目标任务主键
     * @param command 任务修改入参
     * @return 修改并重查全行后的任务视图
     */
    @Transactional
    fun updateTask(definitionId: Long, command: TaskSaveDTO): TaskVO {
        validate(command)
        val existing = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        require(command.taskCode == existing.taskCode) { "任务编码创建后不可修改: ${existing.taskCode}" }
        val updated = taskDefinitionRepository.update(existing, command, ADMIN_API_IDENTITY)
        // 重排失败抛出使 DB 回滚, Quartz 保留旧调度并等待下次变更或对账修复
        synchronizer.scheduleOrReplace(updated)
        replaceTenantSelection(definitionId, command)
        val refreshed = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        return refreshed.toView(taskTenantRepository.findTenantIds(definitionId))
    }

    /**
     * 修改任务启停状态; 启用重建调度面, 停用从调度面移除
     *
     * @param definitionId 目标任务主键
     * @param status 目标启停状态
     * @return 状态变更后的任务视图
     */
    @Transactional
    fun updateTaskStatus(definitionId: Long, status: EnabledStatus): TaskVO {
        val existing = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        if (status == EnabledStatus.ENABLED) {
            synchronizer.ensureScheduled(existing)
        } else {
            synchronizer.remove(definitionId)
        }
        taskDefinitionRepository.updateStatus(existing, status, ADMIN_API_IDENTITY)
        val refreshed = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        return refreshed.toView(taskTenantRepository.findTenantIds(definitionId))
    }

    /**
     * 逻辑删除任务, 移除 Quartz 调度与圈定租户清单, 执行记录保留审计
     *
     * @param definitionId 目标任务主键
     */
    @Transactional
    fun deleteTask(definitionId: Long) {
        val existing = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        synchronizer.remove(definitionId)
        taskDefinitionRepository.delete(existing)
        taskTenantRepository.deleteByDefinitionId(definitionId)
    }

    /**
     * 人工触发一次任务; 同一 requestId 已受理时拒绝
     *
     * @param definitionId 目标任务主键, 必须处于启用状态
     * @param command 触发入参, 提供幂等键
     */
    fun triggerTask(definitionId: Long, command: TaskTriggerDTO?) {
        val definition = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        require(definition.status == EnabledStatus.ENABLED) { "任务未启用, 拒绝触发: ${definition.taskCode}" }
        val requestId = command?.requestId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        require(!taskExecutionRepository.existsByRequestId(definitionId, requestId)) {
            "同一 requestId 的人工触发已受理: $requestId"
        }
        synchronizer.triggerManually(definition, requestId)
    }

    /**
     * 预览任务未来的触发时点
     *
     * @param definitionId 目标任务主键
     * @param count 请求的时点数量, 收敛到 1 到 10
     * @return 未来触发时点列表, 按时间升序; 无未来触发时返回空列表
     */
    fun nextFireTimes(definitionId: Long, count: Int): List<LocalDateTime> {
        val definition = requireNotNull(taskDefinitionRepository.findById(definitionId)) { "任务不存在: $definitionId" }
        return TaskTriggerRules.computeNextFireTimes(definition, count.coerceIn(1, 10), LocalDateTime.now())
    }

    /**
     * 替换圈定租户清单: 仅 SELECTED 圈定写入, 全部租户圈定清空清单
     *
     * @param definitionId 所属任务主键
     * @param command 保存入参
     */
    private fun replaceTenantSelection(definitionId: Long, command: TaskSaveDTO) {
        val tenantIds = if (command.tenantScope == TaskTenantScope.SELECTED_TENANTS) {
            command.tenantIds.distinct().sorted()
        } else {
            emptyList()
        }
        taskTenantRepository.replaceAll(definitionId, tenantIds, ADMIN_API_IDENTITY)
    }

    /**
     * 校验保存入参的触发字段语义、时区、目标地址与圈定清单
     *
     * @param command 待校验的任务新增或修改入参
     */
    private fun validate(command: TaskSaveDTO) {
        when (command.triggerType) {
            TaskTriggerType.CRON -> {
                val cronExpression = requireNotNull(command.cronExpression) { "CRON 任务必须提供 cron 表达式" }
                TaskTriggerRules.requireValidCron(cronExpression)
            }

            TaskTriggerType.FIXED_INTERVAL -> requireNotNull(command.intervalSeconds) { "固定间隔任务必须提供间隔秒数" }

            TaskTriggerType.ONE_TIME -> {
                val fireAt = requireNotNull(command.fireAt) { "一次性任务必须提供触发时点" }
                require(fireAt.isAfter(LocalDateTime.now())) { "一次性任务的触发时点必须在未来" }
            }
        }
        TaskTriggerRules.requireValidTimeZone(command.timezoneId)
        taskHttpGuard.checkSyntax(command.targetUrl)
        if (command.tenantScope == TaskTenantScope.SELECTED_TENANTS) {
            require(command.tenantIds.isNotEmpty()) { "指定租户圈定必须提供至少一个租户" }
        }
        if (command.httpMethod == TaskHttpMethod.GET || command.httpMethod == TaskHttpMethod.DELETE) {
            require(command.body == null) { "GET/DELETE 任务不携带请求体" }
        }
    }

    /**
     * 实体转管理视图
     *
     * @param tenantIds 圈定的租户清单
     * @return 覆盖全部业务列与审计列的任务视图
     */
    private fun TaskDefinitionEntity.toView(tenantIds: List<Long>): TaskVO =
        TaskVO(
            definitionId = definitionId,
            taskCode = taskCode,
            taskName = taskName,
            description = description,
            triggerType = triggerType,
            cronExpression = cronExpression,
            intervalSeconds = intervalSeconds,
            fireAt = fireAt,
            timezoneId = timezoneId,
            httpMethod = httpMethod,
            targetUrl = targetUrl,
            headers = headers ?: emptyMap(),
            body = body,
            timeoutSeconds = timeoutSeconds,
            maxAttempts = maxAttempts,
            backoffSeconds = backoffSeconds,
            tenantScope = tenantScope,
            tenantIds = tenantIds,
            misfirePolicy = misfirePolicy,
            concurrentPolicy = concurrentPolicy,
            ownerAccount = ownerAccount,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * 判断异常链中是否存在唯一键冲突
     *
     * @return 异常链中存在唯一键冲突时为 `true`
     */
    private fun Exception.isDuplicateKeyViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any {
            it is DuplicateKeyException || it is SQLIntegrityConstraintViolationException
        }

    private companion object {
        /**
         * v1 无鉴权时期管理写入的操作人身份
         *
         * RBAC 就绪后由认证上下文替换为真实主体标识
         */
        const val ADMIN_API_IDENTITY = "admin-api:task"
    }
}

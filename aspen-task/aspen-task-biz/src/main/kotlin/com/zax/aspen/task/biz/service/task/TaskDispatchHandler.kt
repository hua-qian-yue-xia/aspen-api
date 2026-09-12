package com.zax.aspen.task.biz.service.task

import com.zax.aspen.admin.api.dto.upm.TenantBriefDto
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.constant.TenantHttpHeaders
import com.zax.aspen.task.api.constant.TaskHttpHeaders
import com.zax.aspen.task.api.enums.TaskConcurrentPolicy
import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskFailureKind
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerSource
import com.zax.aspen.task.biz.config.AspenTaskConfiguration
import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.dispatch.http.TaskHttpDispatcher
import com.zax.aspen.task.biz.dispatch.http.TaskHttpRequest
import com.zax.aspen.task.biz.dispatch.http.TenantTemplateRenderer
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.entity.TaskExecutionEntity
import com.zax.aspen.task.biz.repository.task.TaskDefinitionRepository
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import com.zax.aspen.task.biz.repository.task.TaskTenantRepository
import com.zax.aspen.task.biz.scheduler.quartz.QuartzTaskSynchronizer
import com.zax.aspen.task.biz.tenant.TenantSourceClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 任务投递处理器: 调度触发与执行状态机的编排核心
 *
 * 每轮触发达成「任务 × 租户」展开: 并发策略检查 → 租户圈定解析 → 逐租户插入
 * RUNNING 执行行 (execution_id 主键幂等) → 有界线程池并发 HTTP 投递 → 回写终态;
 * 失败重试由状态机登记一次性 Quartz Trigger (退避后触发), 不阻塞投递线程;
 * 本类经 Scheduler Context 桥接给 Quartz Job 调用, 对外入口统一带 Safely 包装,
 * 异常不外抛到调度线程
 */
@Service
class TaskDispatchHandler(
    private val taskDefinitionRepository: TaskDefinitionRepository,
    private val taskTenantRepository: TaskTenantRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val tenantSourceClient: TenantSourceClient,
    private val httpDispatcher: TaskHttpDispatcher,
    private val synchronizer: QuartzTaskSynchronizer,
    private val properties: AspenTaskProperties,
    @Qualifier(AspenTaskConfiguration.TASK_DISPATCH_EXECUTOR)
    private val dispatchExecutor: ThreadPoolTaskExecutor,
) {
    /**
     * 分发一轮触发的安全入口 (Quartz Job 调用)
     *
     * @param definitionId 任务定义主键
     * @param source 触发来源
     * @param fireTime 计划触发时间
     * @param requestId 人工触发幂等键, 计划触发为 null
     */
    fun dispatchRoundSafely(definitionId: Long, source: TaskTriggerSource, fireTime: LocalDateTime, requestId: String?) {
        try {
            dispatchRound(definitionId, source, fireTime, requestId)
        } catch (e: Exception) {
            log.error("任务分发轮次失败, 本轮跳过: definitionId={}, source={}, fireTime={}", definitionId, source, fireTime, e)
        }
    }

    /**
     * 执行一轮「任务 × 租户」展开与投递
     *
     * @param definitionId 任务定义主键
     * @param source 触发来源
     * @param fireTime 计划触发时间
     * @param requestId 人工触发幂等键, 计划触发为 null
     */
    fun dispatchRound(definitionId: Long, source: TaskTriggerSource, fireTime: LocalDateTime, requestId: String?) {
        val definition = taskDefinitionRepository.findById(definitionId) ?: return
        if (definition.status != EnabledStatus.ENABLED) {
            return
        }
        val tenants = resolveTenants(definition)
        if (tenants.isEmpty()) {
            log.warn("任务无执行租户, 本轮跳过: taskCode={}", definition.taskCode)
            return
        }
        if (definition.concurrentPolicy == TaskConcurrentPolicy.SKIP && taskExecutionRepository.existsRunning(definitionId)) {
            log.info("任务并发策略跳过本轮: taskCode={}, 租户数={}", definition.taskCode, tenants.size)
            tenants.forEach { tenant ->
                taskExecutionRepository.insertSkipped(
                    TaskExecutionIds.build(definitionId, source, fireTime, requestId, tenant.tenantId),
                    definition,
                    tenant.tenantId,
                    source,
                    requestId,
                    fireTime,
                    DISPATCH_IDENTITY,
                )
            }
            return
        }
        log.info("任务分发开始: taskCode={}, source={}, 租户数={}", definition.taskCode, source, tenants.size)
        tenants.forEach { tenant ->
            val execution = taskExecutionRepository.insertRunning(
                TaskExecutionIds.build(definitionId, source, fireTime, requestId, tenant.tenantId),
                definition,
                tenant.tenantId,
                source,
                requestId,
                fireTime,
                DISPATCH_IDENTITY,
            ) ?: return@forEach
            dispatchExecutor.submit { runAttemptSafely(definition, execution, tenant) }
        }
    }

    /**
     * 重试 Trigger 触发的安全入口: 执行非 FAILED 状态时跳过
     *
     * @param executionId 逻辑执行唯一标识
     */
    fun retryFromTriggerSafely(executionId: String) {
        try {
            val execution = taskExecutionRepository.findById(executionId) ?: return
            if (execution.status != TaskExecutionStatus.FAILED) {
                log.debug("重试到达时执行已非 FAILED, 跳过: {}", executionId)
                return
            }
            dispatchRetry(execution)
        } catch (e: Exception) {
            log.error("失败执行重派失败: {}", executionId, e)
        }
    }

    /**
     * 管理端手动重派: 立即对失败执行追加一轮投递
     *
     * @param executionId 逻辑执行唯一标识
     */
    fun retryNow(executionId: String) {
        val execution = requireNotNull(taskExecutionRepository.findById(executionId)) { "执行记录不存在: $executionId" }
        require(execution.status == TaskExecutionStatus.FAILED) { "仅失败执行可重派, 当前状态: ${execution.status.code}" }
        dispatchRetry(execution)
    }

    /**
     * 按定义解析本轮执行的租户集合
     *
     * @param definition 任务定义实体
     * @return 本轮执行的租户简要信息列表
     */
    private fun resolveTenants(definition: TaskDefinitionEntity): List<TenantBriefDto> =
        when (definition.tenantScope) {
            TaskTenantScope.ALL_TENANTS -> tenantSourceClient.listEnabledTenants()

            TaskTenantScope.SELECTED_TENANTS -> {
                val selectedIds = taskTenantRepository.findTenantIds(definition.definitionId)
                if (selectedIds.isEmpty()) {
                    emptyList()
                } else {
                    // 圈定清单是权威执行面; 编码与名称尽力而为, Admin 不可达时以 id 兜底渲染
                    val briefs = runCatching { tenantSourceClient.listEnabledTenants() }.getOrDefault(emptyList())
                        .associateBy { it.tenantId }
                    selectedIds.map { id ->
                        briefs[id] ?: TenantBriefDto(tenantId = id, tenantCode = id.toString(), name = id.toString())
                    }
                }
            }
        }

    /**
     * 重派一个失败执行: 置 RUNNING 并递增轮次后异步投递
     *
     * @param execution 待重派的失败执行
     */
    private fun dispatchRetry(execution: TaskExecutionEntity) {
        val definition = taskDefinitionRepository.findById(execution.definitionId)
        if (definition == null || definition.status != EnabledStatus.ENABLED) {
            log.warn("重派被拒绝, 任务不存在或未启用: executionId={}, definitionId={}", execution.executionId, execution.definitionId)
            return
        }
        val marked = taskExecutionRepository.markRunning(execution, execution.attempt + 1, DISPATCH_IDENTITY)
        if (marked == null) {
            log.debug("重派乐观锁冲突, 已被并发处理: {}", execution.executionId)
            return
        }
        val brief = TenantBriefDto(marked.tenantId, marked.tenantId.toString(), marked.tenantId.toString())
        dispatchExecutor.submit { runAttemptSafely(definition, marked, brief) }
    }

    /**
     * 单次投递的安全包装: 未分类异常也落成 FAILED 终态, 不留僵尸 RUNNING
     *
     * @param definition 任务定义实体
     * @param execution RUNNING 执行实体
     * @param tenant 归属租户简要信息
     */
    private fun runAttemptSafely(definition: TaskDefinitionEntity, execution: TaskExecutionEntity, tenant: TenantBriefDto) {
        try {
            runAttempt(definition, execution, tenant)
        } catch (e: Exception) {
            log.error("任务投递发生未分类异常: taskCode={}, executionId={}", definition.taskCode, execution.executionId, e)
            taskExecutionRepository.complete(
                execution,
                TaskExecutionStatus.FAILED,
                null,
                null,
                null,
                "未分类异常: ${e.message}",
                0,
                DISPATCH_IDENTITY,
            )
        }
    }

    /**
     * 执行一次 HTTP 投递: 渲染租户占位符 → 合并溯源与租户头 → 投递 → 回写终态
     *
     * @param definition 任务定义实体
     * @param execution RUNNING 执行实体
     * @param tenant 归属租户简要信息
     */
    private fun runAttempt(definition: TaskDefinitionEntity, execution: TaskExecutionEntity, tenant: TenantBriefDto) {
        val startedAt = System.nanoTime()
        val headers = buildMap {
            definition.headers.orEmpty().forEach { (name, value) ->
                put(name, TenantTemplateRenderer.render(value, tenant.tenantId, tenant.tenantCode, tenant.name))
            }
            put(TenantHttpHeaders.TENANT_ID, tenant.tenantId.toString())
            put(TaskHttpHeaders.TASK_ID, definition.definitionId.toString())
            put(TaskHttpHeaders.EXECUTION_ID, execution.executionId)
            put(TaskHttpHeaders.ATTEMPT, execution.attempt.toString())
            put(
                TaskHttpHeaders.FIRE_TIME,
                execution.fireTime.atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }
        val result = httpDispatcher.dispatch(
            TaskHttpRequest(
                method = definition.httpMethod,
                url = TenantTemplateRenderer.render(definition.targetUrl, tenant.tenantId, tenant.tenantCode, tenant.name),
                headers = headers,
                body = definition.body?.let { TenantTemplateRenderer.render(it, tenant.tenantId, tenant.tenantCode, tenant.name) },
                timeout = Duration.ofSeconds(definition.timeoutSeconds.toLong()),
            ),
        )
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        val snippet = result.body?.take(properties.http.responseSnippetLength)
        if (result.success) {
            taskExecutionRepository.complete(
                execution,
                TaskExecutionStatus.SUCCESS,
                null,
                result.httpStatus,
                snippet,
                null,
                durationMs,
                DISPATCH_IDENTITY,
            )
            log.info(
                "任务投递成功: taskCode={}, executionId={}, tenantId={}, httpStatus={}, 耗时 {}ms",
                definition.taskCode,
                execution.executionId,
                tenant.tenantId,
                result.httpStatus,
                durationMs,
            )
        } else {
            taskExecutionRepository.complete(
                execution,
                TaskExecutionStatus.FAILED,
                result.failureKind,
                result.httpStatus,
                snippet,
                result.errorMessage?.take(1000),
                durationMs,
                DISPATCH_IDENTITY,
            )
            if (execution.attempt < definition.maxAttempts) {
                synchronizer.scheduleRetry(execution.executionId, definition.backoffSeconds.toLong())
                log.warn(
                    "任务投递失败, 已登记退避重试: taskCode={}, executionId={}, attempt={}/{}, 类别={}",
                    definition.taskCode,
                    execution.executionId,
                    execution.attempt,
                    definition.maxAttempts,
                    result.failureKind?.code,
                )
            } else {
                log.warn(
                    "任务投递失败且重试耗尽: taskCode={}, executionId={}, 类别={}",
                    definition.taskCode,
                    execution.executionId,
                    result.failureKind?.code,
                )
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TaskDispatchHandler::class.java)

        /** 调度链路写入审计列的系统身份 */
        const val DISPATCH_IDENTITY = "system:task-dispatch"
    }
}

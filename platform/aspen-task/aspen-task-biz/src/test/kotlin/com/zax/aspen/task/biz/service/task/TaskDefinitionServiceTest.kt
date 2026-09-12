package com.zax.aspen.task.biz.service.task

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.task.api.dto.TaskSaveDTO
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerType
import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.dispatch.http.TaskHttpGuard
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.entity.TaskDefinitionEntityDraft
import com.zax.aspen.task.biz.repository.task.TaskDefinitionRepository
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import com.zax.aspen.task.biz.repository.task.TaskTenantRepository
import com.zax.aspen.task.biz.scheduler.quartz.QuartzTaskSynchronizer
import com.zax.aspen.task.biz.scheduler.quartz.TaskTriggerRules
import org.mockito.Mockito
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证任务定义服务的写入校验与生命周期编排
 *
 * 仓储与调度同步器以 Mock 替身经构造器注入, 校验逻辑用真实实例
 */
class TaskDefinitionServiceTest {
    /** 任务定义仓储替身 */
    private val taskDefinitionRepository = Mockito.mock(TaskDefinitionRepository::class.java)

    /** 圈定租户仓储替身 */
    private val taskTenantRepository = Mockito.mock(TaskTenantRepository::class.java)

    /** 执行记录仓储替身 */
    private val taskExecutionRepository = Mockito.mock(TaskExecutionRepository::class.java)

    /** Quartz 同步器替身 */
    private val synchronizer = Mockito.mock(QuartzTaskSynchronizer::class.java)

    /** 真实目标校验器 (纯逻辑, 无网络) */
    private val guard = TaskHttpGuard(AspenTaskProperties())

    /** 数据库操作限制 */
    private val limits = DatabaseLimits(defaultPageSize = 20, maxPageSize = 200, defaultBatchSize = 100, maxBatchSize = 500)

    /** 待测服务 */
    private val service = TaskDefinitionService(
        taskDefinitionRepository,
        taskTenantRepository,
        taskExecutionRepository,
        synchronizer,
        guard,
        limits,
    )

    /** 验证编码重复的创建被拒绝且不触碰调度同步器 */
    @Test
    fun `create rejects duplicate task code`() {
        Mockito.`when`(taskDefinitionRepository.findByCode("demo-sync")).thenReturn(entity(taskCode = "demo-sync"))

        assertFailsWith<IllegalArgumentException> { service.createTask(command()) }
        Mockito.verify(synchronizer, Mockito.never()).syncSchedule(entity())
    }

    /** 验证触发字段与触发类型不匹配、时区非法、目标协议非法与空圈定清单被拒绝 */
    @Test
    fun `create validates trigger fields timezone url and tenant selection`() {
        assertFailsWith<IllegalArgumentException> { service.createTask(command(cronExpression = null)) }
        assertFailsWith<IllegalArgumentException> { service.createTask(command(cronExpression = "not-a-cron")) }
        assertFailsWith<IllegalArgumentException> { service.createTask(command(timezoneId = "Mars/Olympus")) }
        assertFailsWith<IllegalArgumentException> { service.createTask(command(targetUrl = "ftp://example.com/x")) }
        assertFailsWith<IllegalArgumentException> { service.createTask(command(tenantScope = TaskTenantScope.SELECTED_TENANTS, tenantIds = emptyList())) }
        assertFailsWith<IllegalArgumentException> { service.createTask(command(triggerType = TaskTriggerType.ONE_TIME, fireAt = LocalDateTime.now().minusDays(1))) }
    }

    /** 验证更新时任务编码不可变更 */
    @Test
    fun `update rejects task code change`() {
        Mockito.`when`(taskDefinitionRepository.findById(1L)).thenReturn(entity(definitionId = 1L, taskCode = "demo-sync"))

        assertFailsWith<IllegalArgumentException> {
            service.updateTask(1L, command(taskCode = "another-code"))
        }
    }

    /** 验证人工触发拒绝未启用任务与已受理的 requestId */
    @Test
    fun `trigger rejects disabled task and duplicate request`() {
        Mockito.`when`(taskDefinitionRepository.findById(1L)).thenReturn(entity(definitionId = 1L, status = EnabledStatus.DISABLED))
        assertFailsWith<IllegalArgumentException> { service.triggerTask(1L, null) }

        Mockito.`when`(taskDefinitionRepository.findById(2L)).thenReturn(entity(definitionId = 2L))
        Mockito.`when`(taskExecutionRepository.existsByRequestId(2L, "req-1")).thenReturn(true)
        assertFailsWith<IllegalArgumentException> { service.triggerTask(2L, com.zax.aspen.task.api.dto.TaskTriggerDTO(requestId = "req-1")) }
    }

    /** 验证触发时点预览收敛数量并按 cron 计算 */
    @Test
    fun `next fire times clamps count and computes from cron`() {
        Mockito.`when`(taskDefinitionRepository.findById(1L)).thenReturn(
            entity(definitionId = 1L, triggerType = TaskTriggerType.CRON, cronExpression = "0 0 * * * ?", intervalSeconds = null, fireAt = null),
        )

        val times = service.nextFireTimes(1L, count = 99)

        assertEquals(10, times.size, "预览数量应收敛到 10")
        assertEquals(times.sorted(), times, "预览时点应升序")
    }

    /** 验证删除任务联动清理调度面与圈定清单 */
    @Test
    fun `delete removes schedule and tenant selection`() {
        val existing = entity(definitionId = 1L)
        Mockito.`when`(taskDefinitionRepository.findById(1L)).thenReturn(existing)

        service.deleteTask(1L)

        Mockito.verify(synchronizer).remove(1L)
        Mockito.verify(taskTenantRepository).deleteByDefinitionId(1L)
        Mockito.verify(taskDefinitionRepository).delete(existing)
    }

    /**
     * 构造合法的保存入参, 按用例覆盖个别字段
     *
     * @param taskCode 任务编码
     * @param triggerType 触发类型
     * @param cronExpression CRON 表达式
     * @param timezoneId IANA 时区
     * @param targetUrl 目标地址
     * @param tenantScope 租户圈定
     * @param tenantIds 圈定租户清单
     * @param fireAt 一次性触发时点
     * @return 覆盖后的保存入参
     */
    private fun command(
        taskCode: String = "demo-sync",
        triggerType: TaskTriggerType = TaskTriggerType.CRON,
        cronExpression: String? = "0 0 2 * * ?",
        timezoneId: String = "Asia/Shanghai",
        targetUrl: String = "https://example.com/hook",
        tenantScope: TaskTenantScope = TaskTenantScope.ALL_TENANTS,
        tenantIds: List<Long> = listOf(100L),
        fireAt: LocalDateTime? = LocalDateTime.now().plusDays(1),
    ): TaskSaveDTO =
        TaskSaveDTO(
            taskCode = taskCode,
            taskName = "演示任务",
            description = null,
            triggerType = triggerType,
            cronExpression = cronExpression,
            intervalSeconds = if (triggerType == TaskTriggerType.FIXED_INTERVAL) 60 else null,
            fireAt = if (triggerType == TaskTriggerType.ONE_TIME) fireAt else null,
            timezoneId = timezoneId,
            httpMethod = TaskHttpMethod.POST,
            targetUrl = targetUrl,
            headers = emptyMap(),
            body = null,
            timeoutSeconds = 30,
            maxAttempts = 1,
            backoffSeconds = 60,
            tenantScope = tenantScope,
            tenantIds = tenantIds,
            ownerAccount = "ops",
        )

    /**
     * 构造任务定义实体夹具
     *
     * @param definitionId 主键
     * @param taskCode 任务编码
     * @param triggerType 触发类型
     * @param cronExpression CRON 表达式
     * @param intervalSeconds 触发间隔秒数
     * @param fireAt 一次性触发时点
     * @param status 启停状态
     * @return 已填充业务列的实体夹具
     */
    private fun entity(
        definitionId: Long = 1L,
        taskCode: String = "demo-sync",
        triggerType: TaskTriggerType = TaskTriggerType.CRON,
        cronExpression: String? = "0 0 2 * * ?",
        intervalSeconds: Int? = null,
        fireAt: LocalDateTime? = null,
        status: EnabledStatus = EnabledStatus.ENABLED,
    ): TaskDefinitionEntity =
        TaskDefinitionEntityDraft.`$`.produce {
            this.definitionId = definitionId
            this.taskCode = taskCode
            taskName = "演示任务"
            description = null
            this.triggerType = triggerType
            this.cronExpression = cronExpression
            this.intervalSeconds = intervalSeconds
            this.fireAt = fireAt
            timezoneId = "Asia/Shanghai"
            httpMethod = TaskHttpMethod.POST
            targetUrl = "https://example.com/hook"
            headers = null
            body = null
            timeoutSeconds = 30
            maxAttempts = 1
            backoffSeconds = 60
            tenantScope = TaskTenantScope.ALL_TENANTS
            misfirePolicy = com.zax.aspen.task.api.enums.TaskMisfirePolicy.FIRE_ONCE
            concurrentPolicy = com.zax.aspen.task.api.enums.TaskConcurrentPolicy.SKIP
            ownerAccount = "ops"
            this.status = status
            version = 1
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
}

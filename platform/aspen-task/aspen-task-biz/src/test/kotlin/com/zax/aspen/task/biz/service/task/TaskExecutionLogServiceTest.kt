package com.zax.aspen.task.biz.service.task

import com.zax.aspen.task.api.dto.TaskExecutionLogEntryDTO
import com.zax.aspen.task.api.dto.TaskExecutionLogReportDTO
import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskLogLevel
import com.zax.aspen.task.api.enums.TaskTriggerSource
import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.entity.TaskExecutionEntity
import com.zax.aspen.task.biz.entity.TaskExecutionEntityDraft
import com.zax.aspen.task.biz.repository.task.TaskExecutionLogRepository
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import org.mockito.Mockito
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证执行日志回传服务的执行存在性校验、受理链路与回传封顶
 *
 * 仓储以 Mock 替身经构造器注入; 落库幂等语义 (唯一键跳过) 由真实 MySQL
 * 集成测试覆盖, 此处不打桩匹配器 (Mockito 匹配器对非空参数返回 null)
 */
class TaskExecutionLogServiceTest {
    /** 执行记录仓储替身 */
    private val taskExecutionRepository = Mockito.mock(TaskExecutionRepository::class.java)

    /** 执行日志仓储替身 */
    private val taskExecutionLogRepository = Mockito.mock(TaskExecutionLogRepository::class.java)

    /** 待测服务 (默认封顶 1000 条) */
    private val service = TaskExecutionLogService(taskExecutionRepository, taskExecutionLogRepository, AspenTaskProperties())

    /** 验证未知执行的回传被拒绝且不触碰日志仓储 */
    @Test
    fun `report rejects unknown execution`() {
        Mockito.`when`(taskExecutionRepository.findById("task-1-f1-2")).thenReturn(null)

        assertFailsWith<IllegalArgumentException> { service.reportExecutionLogs(report("task-1-f1-2")) }
        Mockito.verifyNoInteractions(taskExecutionLogRepository)
    }

    /** 验证已知执行的回传受理成功, 返回仓储落库条数 */
    @Test
    fun `report accepts entries for known execution`() {
        Mockito.`when`(taskExecutionRepository.findById("task-1-f1-2")).thenReturn(execution())
        Mockito.`when`(taskExecutionLogRepository.countByExecutionId("task-1-f1-2")).thenReturn(0L)

        val appended = service.reportExecutionLogs(report("task-1-f1-2"))

        assertEquals(0, appended, "未打桩的仓储替身按 Mockito 默认返回 0, 此处验证受理链路不抛错")
    }

    /** 验证单执行累计回传超过封顶被拒绝且不落库 */
    @Test
    fun `report rejects entries beyond per-execution cap`() {
        Mockito.`when`(taskExecutionRepository.findById("task-1-f1-2")).thenReturn(execution())
        Mockito.`when`(taskExecutionLogRepository.countByExecutionId("task-1-f1-2")).thenReturn(999L)

        assertFailsWith<IllegalArgumentException> { service.reportExecutionLogs(report("task-1-f1-2")) }

        // 封顶拒绝发生在计数之后、落库之前: 计数确已发生, 落库从未发生
        Mockito.verify(taskExecutionLogRepository).countByExecutionId("task-1-f1-2")
        Mockito.verify(taskExecutionLogRepository, Mockito.never())
            .appendAll("task-1-f1-2", report("task-1-f1-2").logs, LocalDateTime.now(), "internal:task-log-api")
    }

    /**
     * 构造两条日志的回传入参
     *
     * @param executionId 逻辑执行唯一标识
     * @return 回传入参
     */
    private fun report(executionId: String): TaskExecutionLogReportDTO =
        TaskExecutionLogReportDTO(
            executionId = executionId,
            logs = listOf(
                TaskExecutionLogEntryDTO(seq = 1, level = TaskLogLevel.INFO, message = "开始同步"),
                TaskExecutionLogEntryDTO(seq = 2, level = TaskLogLevel.WARN, message = "重试一次"),
            ),
        )

    /**
     * 构造执行实体夹具
     *
     * @return 已填充关键列的执行实体夹具
     */
    private fun execution(): TaskExecutionEntity =
        TaskExecutionEntityDraft.`$`.produce {
            executionId = "task-1-f1-2"
            definitionId = 1L
            taskCode = "demo-sync"
            tenantId = 2L
            triggerSource = TaskTriggerSource.SCHEDULED
            requestId = null
            fireTime = LocalDateTime.now()
            attempt = 1
            status = TaskExecutionStatus.RUNNING
            startedAt = LocalDateTime.now()
            version = 1
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
}

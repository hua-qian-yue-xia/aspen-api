package com.zax.aspen.task.biz.service.task

import com.zax.aspen.task.api.dto.TaskExecutionLogReportDTO
import com.zax.aspen.task.api.vo.TaskExecutionLogVO
import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.entity.TaskExecutionLogEntity
import com.zax.aspen.task.biz.repository.task.TaskExecutionLogRepository
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 执行过程日志的回传与查询
 *
 * 回传面向被投递的内部服务 (internal 契约): 校验执行存在后逐条追加, 重复
 * (executionId, seq) 幂等跳过; 无鉴权内网通道必须有界——单逻辑执行的回传
 * 条数 (跨批次累计) 超过 [AspenTaskProperties.LogApi.maxEntriesPerExecution]
 * 即拒绝, 管理端读取同源封顶; 查询面向管理端, 按 seq 升序还原执行走向
 */
@Service
class TaskExecutionLogService(
    private val taskExecutionRepository: TaskExecutionRepository,
    private val taskExecutionLogRepository: TaskExecutionLogRepository,
    private val properties: AspenTaskProperties,
) {
    /**
     * 受理一批执行过程日志回传
     *
     * @param report 回传入参, 同一执行的多条日志一次提交
     * @return 实际落库的条数 (重复条目被幂等跳过)
     */
    fun reportExecutionLogs(report: TaskExecutionLogReportDTO): Int {
        requireNotNull(taskExecutionRepository.findById(report.executionId)) {
            "执行记录不存在: ${report.executionId}"
        }
        val maxEntries = properties.logApi.maxEntriesPerExecution
        val existing = taskExecutionLogRepository.countByExecutionId(report.executionId)
        require(existing + report.logs.size <= maxEntries) {
            "执行日志回传条数超过上限 (已回传 $existing, 本批 ${report.logs.size}, 上限 $maxEntries): ${report.executionId}"
        }
        val appended = taskExecutionLogRepository.appendAll(
            executionId = report.executionId,
            entries = report.logs,
            loggedAtFallback = LocalDateTime.now(),
            identity = LOG_API_IDENTITY,
        )
        if (appended < report.logs.size) {
            log.debug(
                "执行日志回传含重复条目, 已幂等跳过: executionId={}, 总数={}, 落库={}",
                report.executionId,
                report.logs.size,
                appended,
            )
        }
        return appended
    }

    /**
     * 查询逻辑执行的过程日志, 按 seq 升序, 读取条数与回传上限同源封顶
     *
     * @param executionId 逻辑执行唯一标识
     * @return 过程日志视图列表, 无回传日志时返回空列表
     */
    fun executionLogs(executionId: String): List<TaskExecutionLogVO> =
        taskExecutionLogRepository
            .findByExecutionId(executionId, properties.logApi.maxEntriesPerExecution)
            .map { it.toView() }

    /**
     * 实体转管理视图
     *
     * @return 覆盖日志全部业务列与审计列的视图
     */
    private fun TaskExecutionLogEntity.toView(): TaskExecutionLogVO =
        TaskExecutionLogVO(
            logId = logId,
            executionId = executionId,
            seq = seq,
            level = level,
            message = message,
            loggedAt = loggedAt,
            createdAt = createdAt,
        )

    private companion object {
        private val log = LoggerFactory.getLogger(TaskExecutionLogService::class.java)

        /** 内部回传链路写入审计列的系统身份 */
        const val LOG_API_IDENTITY = "internal:task-log-api"
    }
}

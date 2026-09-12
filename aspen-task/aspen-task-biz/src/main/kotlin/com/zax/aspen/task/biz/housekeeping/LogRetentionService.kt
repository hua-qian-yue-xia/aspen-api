package com.zax.aspen.task.biz.housekeeping

import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.repository.task.TaskExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 执行记录保留期治理
 *
 * 平台自持家: 按保留天数分批物理删除过期执行记录, 并回收超过回收窗口仍未回写的
 * 僵尸 RUNNING 执行 (投递实例崩溃遗留); 由系统 Quartz Job 每日触发与启动对账器
 * 各执行一次, 两处触发幂等
 */
@Service
class LogRetentionService(
    private val taskExecutionRepository: TaskExecutionRepository,
    private val properties: AspenTaskProperties,
) {
    /**
     * 物理删除超过保留期的执行记录, 分批循环直至清空
     *
     * @return 本次删除的记录总数, 保留期治理关闭时返回 0
     */
    fun purgeExpired(): Int {
        val retentionDays = properties.housekeeping.logRetentionDays
        if (retentionDays <= 0) {
            return 0
        }
        val cutoff = LocalDateTime.now().minusDays(retentionDays.toLong())
        var removed = 0
        while (true) {
            val ids = taskExecutionRepository.findExpiredIds(cutoff, BATCH_SIZE)
            if (ids.isEmpty()) {
                break
            }
            taskExecutionRepository.deleteByIds(ids)
            removed += ids.size
        }
        if (removed > 0) {
            log.info("执行记录保留期清理完成: 删除 {} 条 (保留 {} 天)", removed, retentionDays)
        }
        return removed
    }

    /**
     * 回收僵尸 RUNNING 执行: 超过回收窗口未回写的行置为实例中断终态
     *
     * @return 本次回收的记录数
     */
    fun sweepStaleRunning(): Int {
        val cutoff = LocalDateTime.now().minusMinutes(properties.housekeeping.staleRunningMinutes.toLong())
        var swept = 0
        while (true) {
            val stale = taskExecutionRepository.findStaleRunning(cutoff, BATCH_SIZE)
            if (stale.isEmpty()) {
                break
            }
            stale.forEach { execution ->
                taskExecutionRepository.markInterrupted(execution, RETENTION_IDENTITY)
                swept++
            }
        }
        if (swept > 0) {
            log.warn("僵尸 RUNNING 执行回收完成: {} 条置为实例中断", swept)
        }
        return swept
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LogRetentionService::class.java)

        /** 保留期清理与僵尸回收的单批数量 */
        const val BATCH_SIZE = 500

        /** 治理写入审计列的系统身份 */
        const val RETENTION_IDENTITY = "system:task-retention"
    }
}

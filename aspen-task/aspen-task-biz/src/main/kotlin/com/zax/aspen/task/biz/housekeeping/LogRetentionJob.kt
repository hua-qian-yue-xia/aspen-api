package com.zax.aspen.task.biz.housekeeping

import com.zax.aspen.task.biz.scheduler.quartz.QuartzTaskKeys
import org.quartz.Job
import org.quartz.JobExecutionContext

/**
 * 执行记录保留期清理的系统 Quartz Job 壳
 *
 * 由 [com.zax.aspen.task.biz.scheduler.quartz.QuartzTaskSynchronizer.replaceHousekeepingJob]
 * 登记的每日系统 Trigger 触发, 经 Scheduler Context 桥接调用治理服务;
 * 业务任务保持「只支持 HTTP 投递」不变, 平台自持家直接调用本服务 Service
 */
class LogRetentionJob : Job {
    /**
     * 执行一次保留期清理 (执行记录与过程日志) 与僵尸运行态回收
     *
     * @param context Quartz 触发上下文, 提供 Scheduler Context
     */
    override fun execute(context: JobExecutionContext) {
        val service = context.scheduler.getContext()[QuartzTaskKeys.CONTEXT_LOG_RETENTION] as LogRetentionService
        service.purgeExpired()
        service.purgeExpiredLogs()
        service.sweepStaleRunning()
    }
}

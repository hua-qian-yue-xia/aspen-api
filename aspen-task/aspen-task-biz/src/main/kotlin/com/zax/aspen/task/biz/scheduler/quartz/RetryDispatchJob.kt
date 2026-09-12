package com.zax.aspen.task.biz.scheduler.quartz

import org.quartz.Job
import org.quartz.JobExecutionContext

/**
 * 失败重派的 Quartz Job 壳
 *
 * 由 [QuartzTaskSynchronizer] 登记的一次性 Trigger 触发: 从 JobDataMap 读逻辑
 * 执行标识, 经 Scheduler Context 桥接调用投递处理器; 执行已非 FAILED 状态时
 * 处理器自行跳过, 因此重复登记是安全的
 */
class RetryDispatchJob : Job {
    /**
     * 重派一个失败执行
     *
     * @param context Quartz 触发上下文, 提供 JobDataMap
     */
    override fun execute(context: JobExecutionContext) {
        val handler = context.scheduler.getContext()[QuartzTaskKeys.CONTEXT_DISPATCH_HANDLER]
            as com.zax.aspen.task.biz.service.task.TaskDispatchHandler
        val executionId = context.mergedJobDataMap.getString(QuartzTaskKeys.DATA_EXECUTION_ID)
        handler.retryFromTriggerSafely(executionId)
    }
}

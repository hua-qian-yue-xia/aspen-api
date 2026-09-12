package com.zax.aspen.task.biz.scheduler.quartz

import com.zax.aspen.task.api.enums.TaskTriggerSource
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 任务分发的 Quartz Job 壳
 *
 * 全部任务共用的唯一 Job 类: 从 Scheduler Context 桥接拿投递处理器 (Quartz Job
 * 实例由框架反射创建, 不做 Spring 注入), 从 mergedJobDataMap 读定义主键与来源
 * (人工触发经 triggerJob 数据覆盖携带 MANUAL 与 requestId, 计划触发缺省
 * SCHEDULED), 任务配置由处理器现读定义表, 不在 JobDataMap 快照 (防参数陈旧)
 */
class TaskDispatchJob : Job {
    /**
     * 执行一次分发轮次
     *
     * @param context Quartz 触发上下文, 提供 mergedJobDataMap 与计划触发时间
     */
    override fun execute(context: JobExecutionContext) {
        val handler = context.scheduler.getContext()[QuartzTaskKeys.CONTEXT_DISPATCH_HANDLER]
            as com.zax.aspen.task.biz.service.task.TaskDispatchHandler
        val data = context.mergedJobDataMap
        val definitionId = data.getLong(QuartzTaskKeys.DATA_DEFINITION_ID)
        val source = runCatching { TaskTriggerSource.valueOf(data.getString(QuartzTaskKeys.DATA_TRIGGER_SOURCE)) }
            .getOrDefault(TaskTriggerSource.SCHEDULED)
        val requestId = data.getString(QuartzTaskKeys.DATA_REQUEST_ID)
        val fireTime = context.scheduledFireTime
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()
            ?: LocalDateTime.now()
        handler.dispatchRoundSafely(definitionId, source, fireTime, requestId)
    }
}

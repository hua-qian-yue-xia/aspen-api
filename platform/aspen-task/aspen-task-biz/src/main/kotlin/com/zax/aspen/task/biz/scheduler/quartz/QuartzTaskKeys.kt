package com.zax.aspen.task.biz.scheduler.quartz

import org.quartz.JobKey
import org.quartz.TriggerKey

/**
 * Task 服务与 Quartz 运行时的键与数据约定
 *
 * JobKey 命名: 任务定义 task-{definitionId} / 重试 retry-{executionId} / 系统
 * housekeeping 各占独立分组; JobDataMap 只携带定位键, 任务配置每次投递现读定义表
 */
object QuartzTaskKeys {
    /** 任务定义 Job/Trigger 分组 */
    const val JOB_GROUP = "aspen-task"

    /** 失败重试一次性 Trigger 分组 */
    const val RETRY_JOB_GROUP = "aspen-task-retry"

    /** 平台自持家系统 Job 分组 (执行记录保留期清理) */
    const val SYSTEM_JOB_GROUP = "aspen-task-system"

    /** JobDataMap: 任务定义主键 */
    const val DATA_DEFINITION_ID = "definitionId"

    /** JobDataMap: 逻辑执行唯一标识 (重试 Job 使用) */
    const val DATA_EXECUTION_ID = "executionId"

    /** JobDataMap: 触发来源 (人工触发时覆盖写入) */
    const val DATA_TRIGGER_SOURCE = "triggerSource"

    /** JobDataMap: 人工触发幂等键 */
    const val DATA_REQUEST_ID = "requestId"

    /** Scheduler Context: 投递处理器的桥接键 (Quartz Job 不做 Spring 注入) */
    const val CONTEXT_DISPATCH_HANDLER = "aspenTaskDispatchHandler"

    /** Scheduler Context: 保留期清理服务的桥接键 */
    const val CONTEXT_LOG_RETENTION = "aspenLogRetentionService"

    /**
     * 构造任务定义的 JobKey
     *
     * @param definitionId 任务定义主键
     * @return 分组 aspen-task 下的任务 JobKey
     */
    fun jobKey(definitionId: Long): JobKey = JobKey.jobKey("task-$definitionId", JOB_GROUP)

    /**
     * 构造任务定义的 TriggerKey, 与 JobKey 同名
     *
     * @param definitionId 任务定义主键
     * @return 分组 aspen-task 下的任务 TriggerKey
     */
    fun triggerKey(definitionId: Long): TriggerKey = TriggerKey.triggerKey("task-$definitionId", JOB_GROUP)

    /**
     * 构造失败重试的 JobKey
     *
     * @param executionId 逻辑执行唯一标识
     * @return 分组 aspen-task-retry 下的重试 JobKey
     */
    fun retryJobKey(executionId: String): JobKey = JobKey.jobKey("retry-$executionId", RETRY_JOB_GROUP)

    /**
     * 构造失败重试的 TriggerKey, 与重试 JobKey 同名
     *
     * @param executionId 逻辑执行唯一标识
     * @return 分组 aspen-task-retry 下的重试 TriggerKey
     */
    fun retryTriggerKey(executionId: String): TriggerKey = TriggerKey.triggerKey("retry-$executionId", RETRY_JOB_GROUP)

    /**
     * 构造执行记录保留期清理的系统 JobKey
     *
     * @return 分组 aspen-task-system 下的清理 JobKey
     */
    fun housekeepingJobKey(): JobKey = JobKey.jobKey("log-retention", SYSTEM_JOB_GROUP)
}

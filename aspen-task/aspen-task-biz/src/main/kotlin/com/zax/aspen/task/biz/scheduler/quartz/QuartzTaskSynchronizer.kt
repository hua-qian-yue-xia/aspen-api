package com.zax.aspen.task.biz.scheduler.quartz

import com.zax.aspen.task.api.enums.TaskTriggerSource
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.housekeeping.LogRetentionJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.ObjectAlreadyExistsException
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * 任务定义与 Quartz 运行时的同步器
 *
 * 定义表是唯一权威: 全部操作「先 Quartz 后 DB」由 Service 编排, 本类只封装
 * Scheduler 操作并保证幂等; Quartz 使用自身连接独立提交, 与定义表事务之间的
 * 窗口期漂移由启动对账器闭环 (技术架构 14.1.3 受控选择)
 */
@Component
class QuartzTaskSynchronizer(
    private val scheduler: Scheduler,
) {
    /**
     * 以任务定义整体重建 Job 与 Trigger (先删后建), 供新增、更新与对账
     *
     * @param definition 任务定义实体
     */
    fun scheduleOrReplace(definition: TaskDefinitionEntity) {
        val jobKey = QuartzTaskKeys.jobKey(definition.definitionId)
        scheduler.deleteJob(jobKey)
        scheduler.scheduleJob(TaskTriggerRules.buildJobDetail(definition), TaskTriggerRules.buildTrigger(definition))
    }

    /**
     * 确保 Job/Trigger 存在, 缺失 (一次性已触发或停用被清理) 时按定义重建
     *
     * @param definition 任务定义实体
     */
    fun ensureScheduled(definition: TaskDefinitionEntity) {
        if (scheduler.getTrigger(QuartzTaskKeys.triggerKey(definition.definitionId)) == null) {
            scheduleOrReplace(definition)
        }
    }

    /**
     * 从调度面移除任务 (停用与删除共用), Job 与 Trigger 一并清理
     *
     * @param definitionId 任务定义主键
     */
    fun remove(definitionId: Long) {
        scheduler.deleteJob(QuartzTaskKeys.jobKey(definitionId))
    }

    /**
     * 人工触发一次任务: 确保 Job 存在后立即触发, 数据覆盖携带来源与幂等键
     *
     * @param definition 启用中的任务定义实体
     * @param requestId 人工触发幂等键
     */
    fun triggerManually(definition: TaskDefinitionEntity, requestId: String) {
        ensureScheduled(definition)
        scheduler.triggerJob(
            QuartzTaskKeys.jobKey(definition.definitionId),
            JobDataMap(
                mapOf(
                    QuartzTaskKeys.DATA_TRIGGER_SOURCE to TaskTriggerSource.MANUAL.name,
                    QuartzTaskKeys.DATA_REQUEST_ID to requestId,
                ),
            ),
        )
    }

    /**
     * 登记失败重试的一次性 Trigger, 延迟退避秒数后触发重试 Job
     *
     * 同一逻辑执行的重试 Trigger 已存在时跳过 (重复登记幂等)
     *
     * @param executionId 逻辑执行唯一标识
     * @param backoffSeconds 退避延迟秒数, 0 表示立即
     */
    fun scheduleRetry(executionId: String, backoffSeconds: Long) {
        val jobKey = QuartzTaskKeys.retryJobKey(executionId)
        try {
            scheduler.scheduleJob(
                JobBuilder.newJob(RetryDispatchJob::class.java)
                    .withIdentity(jobKey)
                    .usingJobData(QuartzTaskKeys.DATA_EXECUTION_ID, executionId)
                    .requestRecovery(false)
                    .build(),
                TriggerBuilder.newTrigger()
                    .withIdentity(QuartzTaskKeys.retryTriggerKey(executionId))
                    .forJob(jobKey)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                    .startAt(Date.from(LocalDateTime.now().plusSeconds(backoffSeconds).atZone(ZoneId.systemDefault()).toInstant()))
                    .build(),
            )
        } catch (e: ObjectAlreadyExistsException) {
            log.debug("逻辑执行的重试 Trigger 已存在, 跳过登记: {}", executionId)
        }
    }

    /**
     * 登记执行记录保留期清理的系统 Job (每日 cron), 已存在时整体重建
     *
     * @param cron 清理触发 CRON 表达式
     * @param timezoneId 清理触发时区
     */
    fun replaceHousekeepingJob(cron: String, timezoneId: String) {
        val jobKey = QuartzTaskKeys.housekeepingJobKey()
        scheduler.deleteJob(jobKey)
        scheduler.scheduleJob(
            JobBuilder.newJob(LogRetentionJob::class.java)
                .withIdentity(jobKey)
                .build(),
            TriggerBuilder.newTrigger()
                .withIdentity(jobKey.name, QuartzTaskKeys.SYSTEM_JOB_GROUP)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(java.util.TimeZone.getTimeZone(timezoneId)))
                .build(),
        )
    }

    /**
     * 列出任务定义分组下的全部 JobKey, 供对账器发现孤儿 Job
     *
     * @return 分组 aspen-task 内的全部 JobKey
     */
    fun listTaskJobKeys(): Set<JobKey> = scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.groupEquals(QuartzTaskKeys.JOB_GROUP))

    /**
     * 查询任务当前 Trigger 的下次触发时间
     *
     * @param definitionId 任务定义主键
     * @return 下次触发时间, Trigger 不存在时返回 `null`
     */
    fun nextFireTime(definitionId: Long): LocalDateTime? =
        scheduler.getTrigger(QuartzTaskKeys.triggerKey(definitionId))?.nextFireTime
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()

    private companion object {
        private val log = LoggerFactory.getLogger(QuartzTaskSynchronizer::class.java)
    }
}

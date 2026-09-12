package com.zax.aspen.task.biz.scheduler.quartz

import com.zax.aspen.task.api.enums.TaskMisfirePolicy
import com.zax.aspen.task.api.enums.TaskTriggerType
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.quartz.CronExpression
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/**
 * 任务定义到 Quartz JobDetail/Trigger 的构造规则与触发时点计算
 *
 * 纯函数集合, 不触碰 Scheduler 实例; misfire 指令按任务配置显式映射, 禁止
 * 依赖 Quartz 默认行为 (技术架构 14.1.3); 全部触发时间显式携带任务时区
 */
object TaskTriggerRules {
    /**
     * 校验 CRON 表达式合法性
     *
     * @param expression 待校验的 Quartz 6/7 位制 CRON 表达式
     * @throws IllegalArgumentException 表达式非法时抛出
     */
    fun requireValidCron(expression: String) {
        require(CronExpression.isValidExpression(expression)) { "CRON 表达式非法: $expression" }
    }

    /**
     * 校验并解析 IANA 时区标识
     *
     * @param timezoneId 时区标识文本
     * @return 解析后的时区
     * @throws IllegalArgumentException 时区标识无法识别时抛出
     */
    fun requireValidTimeZone(timezoneId: String): ZoneId =
        try {
            ZoneId.of(timezoneId)
        } catch (e: Exception) {
            throw IllegalArgumentException("时区标识非法: $timezoneId", e)
        }

    /**
     * 构造任务定义的 JobDetail, JobDataMap 只携带定义主键
     *
     * @param definition 任务定义实体
     * @return 分发 Job 的 JobDetail
     */
    fun buildJobDetail(definition: TaskDefinitionEntity): JobDetail =
        JobBuilder.newJob(TaskDispatchJob::class.java)
            .withIdentity(QuartzTaskKeys.jobKey(definition.definitionId))
            .usingJobData(QuartzTaskKeys.DATA_DEFINITION_ID, definition.definitionId)
            .requestRecovery(false)
            .build()

    /**
     * 构造任务定义的触发 Trigger, 按触发类型与 misfire 策略映射指令
     *
     * @param definition 任务定义实体
     * @return 与任务配置一致的 Trigger
     * @throws IllegalArgumentException 触发字段与触发类型不匹配时抛出
     */
    fun buildTrigger(definition: TaskDefinitionEntity): Trigger {
        val zone = requireValidTimeZone(definition.timezoneId)
        val key = QuartzTaskKeys.triggerKey(definition.definitionId)
        val jobKey = QuartzTaskKeys.jobKey(definition.definitionId)
        return when (definition.triggerType) {
            TaskTriggerType.CRON -> {
                val expression = requireNotNull(definition.cronExpression) { "CRON 任务缺少 cron 表达式: ${definition.taskCode}" }
                requireValidCron(expression)
                val schedule = CronScheduleBuilder.cronSchedule(expression)
                    .inTimeZone(TimeZone.getTimeZone(zone))
                when (definition.misfirePolicy) {
                    TaskMisfirePolicy.FIRE_ONCE -> schedule.withMisfireHandlingInstructionFireAndProceed()
                    TaskMisfirePolicy.SKIP -> schedule.withMisfireHandlingInstructionDoNothing()
                }
                TriggerBuilder.newTrigger()
                    .withIdentity(key)
                    .forJob(jobKey)
                    .withSchedule(schedule)
                    .build()
            }

            TaskTriggerType.FIXED_INTERVAL -> {
                val intervalSeconds = requireNotNull(definition.intervalSeconds) {
                    "固定间隔任务缺少间隔秒数: ${definition.taskCode}"
                }
                val schedule = SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(intervalSeconds)
                    .repeatForever()
                when (definition.misfirePolicy) {
                    // 补执行一次: 立即补偿错过的触发; 跳过: 丢弃错过时点按下一时点继续
                    TaskMisfirePolicy.FIRE_ONCE -> schedule.withMisfireHandlingInstructionFireNow()
                    TaskMisfirePolicy.SKIP -> schedule.withMisfireHandlingInstructionNextWithExistingCount()
                }
                TriggerBuilder.newTrigger()
                    .withIdentity(key)
                    .forJob(jobKey)
                    .withSchedule(schedule)
                    .build()
            }

            TaskTriggerType.ONE_TIME -> {
                val fireAt = requireNotNull(definition.fireAt) { "一次性任务缺少触发时点: ${definition.taskCode}" }
                val schedule = SimpleScheduleBuilder.simpleSchedule()
                when (definition.misfirePolicy) {
                    // 一次性 Trigger 无下一时点, 跳过策略下错过即不再触发, 触发器自然完结
                    TaskMisfirePolicy.FIRE_ONCE -> schedule.withMisfireHandlingInstructionFireNow()
                    TaskMisfirePolicy.SKIP -> schedule.withMisfireHandlingInstructionNextWithExistingCount()
                }
                TriggerBuilder.newTrigger()
                    .withIdentity(key)
                    .forJob(jobKey)
                    .withSchedule(schedule)
                    .startAt(Date.from(fireAt.atZone(zone).toInstant()))
                    .build()
            }
        }
    }

    /**
     * 比对 Quartz 现存 Trigger 与按定义新建的 Trigger 的调度语义是否一致
     *
     * 只比较定义派生的调度属性 (触发器类型、CRON/间隔/起始时点、时区、misfire 指令),
     * 不比较 nextFireTime 等运行态——一致的 Trigger 保留运行态, 待补偿的 misfire
     * 状态不被抹掉 (供对账器判断漂移, 2026-09-13 审核修复)
     *
     * @param existing Quartz 调度面上的现存 Trigger
     * @param expected 按当前定义构造的期望 Trigger
     * @return 调度语义一致时为 true, 类型不同或任一调度属性漂移时为 false
     */
    fun isSameSchedule(existing: Trigger, expected: Trigger): Boolean {
        if (existing.misfireInstruction != expected.misfireInstruction) {
            return false
        }
        return when {
            existing is CronTrigger && expected is CronTrigger ->
                existing.cronExpression == expected.cronExpression && existing.timeZone == expected.timeZone

            existing is SimpleTrigger && expected is SimpleTrigger ->
                existing.repeatInterval == expected.repeatInterval && existing.startTime == expected.startTime

            else -> false
        }
    }

    /**
     * 计算任务未来的触发时点, 不触碰 Scheduler, 供保存前预览与详情展示
     *
     * @param definition 任务定义实体
     * @param count 需要的时点数量
     * @param fromNow 计算起点时间
     * @return 未来触发时点列表 (转任务时区本地时间), 按时间升序; 无未来触发时返回空列表
     */
    fun computeNextFireTimes(definition: TaskDefinitionEntity, count: Int, fromNow: LocalDateTime): List<LocalDateTime> {
        if (count <= 0) {
            return emptyList()
        }
        val zone = requireValidTimeZone(definition.timezoneId)
        return when (definition.triggerType) {
            TaskTriggerType.CRON -> {
                val expression = requireNotNull(definition.cronExpression) { "CRON 任务缺少 cron 表达式: ${definition.taskCode}" }
                requireValidCron(expression)
                val cron = CronExpression(expression)
                cron.timeZone = TimeZone.getTimeZone(zone)
                val fromInstant = fromNow.atZone(ZoneId.systemDefault()).toInstant()
                val result = mutableListOf<LocalDateTime>()
                var next: Date? = cron.getNextValidTimeAfter(Date.from(fromInstant))
                while (next != null && result.size < count) {
                    result.add(LocalDateTime.ofInstant(next.toInstant(), zone))
                    next = cron.getNextValidTimeAfter(next)
                }
                result
            }

            TaskTriggerType.FIXED_INTERVAL -> {
                val intervalSeconds = requireNotNull(definition.intervalSeconds) {
                    "固定间隔任务缺少间隔秒数: ${definition.taskCode}"
                }
                (1..count).map { index ->
                    fromNow.plusSeconds(intervalSeconds.toLong() * index)
                }
            }

            TaskTriggerType.ONE_TIME -> {
                val fireAt = requireNotNull(definition.fireAt) { "一次性任务缺少触发时点: ${definition.taskCode}" }
                if (fireAt.isAfter(fromNow)) listOf(fireAt) else emptyList()
            }
        }
    }
}

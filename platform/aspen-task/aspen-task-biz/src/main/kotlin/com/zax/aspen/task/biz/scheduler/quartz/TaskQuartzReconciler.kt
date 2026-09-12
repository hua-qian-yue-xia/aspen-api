package com.zax.aspen.task.biz.scheduler.quartz

import com.zax.aspen.task.biz.config.AspenTaskProperties
import com.zax.aspen.task.biz.housekeeping.LogRetentionService
import com.zax.aspen.task.biz.repository.task.TaskDefinitionRepository
import org.quartz.Scheduler
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 启动对账器: 修复任务定义与 Quartz 运行时之间的漂移
 *
 * 每个实例启动时执行 (操作幂等, 集群并发由 Quartz 数据库锁兜底):
 * 1. 逐个启用定义对账调度面——缺失补建、漂移告警后原子重排、语义一致的健康
 *    Trigger 保留运行态 (待补偿的 misfire 不被抹掉, 处理器桥接由
 *    [SchedulerContextBridge] 在调度器启动前完成, 本类不再负责桥接);
 * 2. 清理调度面孤儿 (Job 对应的定义不存在或未启用);
 * 3. 回收僵尸 RUNNING 执行、登记保留期清理系统 Job。
 * 任一步失败只记录错误不阻断启动, 等待下次重启或变更自愈 (对齐 Admin 路由首发模式)
 */
@Component
class TaskQuartzReconciler(
    private val scheduler: Scheduler,
    private val synchronizer: QuartzTaskSynchronizer,
    private val taskDefinitionRepository: TaskDefinitionRepository,
    private val logRetentionService: LogRetentionService,
    private val properties: AspenTaskProperties,
) : ApplicationRunner {
    /**
     * 执行启动对账
     *
     * @param args 启动参数, 未使用
     */
    override fun run(args: ApplicationArguments) {
        reconcileDefinitionsSafely()
        runSafely("回收僵尸运行态") { logRetentionService.sweepStaleRunning() }
        runSafely("登记保留期清理系统任务") {
            if (properties.housekeeping.logRetentionDays > 0) {
                synchronizer.replaceHousekeepingJob(
                    properties.housekeeping.cron,
                    properties.housekeeping.timezoneId,
                )
            }
        }
        log.info(
            "Task 启动对账完成; 租户来源基地址: {}, 内部目标白名单: {}",
            properties.tenantSource.baseUrl.ifBlank { "未配置" },
            properties.http.allowedInternalHosts,
        )
    }

    /**
     * 逐个启用定义对账调度面并清理孤儿 Job
     */
    private fun reconcileDefinitionsSafely() {
        runSafely("对账任务定义与调度面") {
            val definitions = taskDefinitionRepository.findAllEnabled()
            val enabledIds = definitions.map { it.definitionId }.toSet()
            definitions.forEach { definition ->
                runSafely("对账任务调度: ${definition.taskCode}") { synchronizer.reconcileSchedule(definition) }
            }
            synchronizer.listTaskJobKeys().forEach { jobKey ->
                val definitionId = jobKey.name.removePrefix("task-").toLongOrNull()
                if (definitionId == null || definitionId !in enabledIds) {
                    runSafely("清理孤儿调度: $jobKey") { scheduler.deleteJob(jobKey) }
                    log.warn("清理无启用定义的孤儿 Quartz Job: {}", jobKey)
                }
            }
        }
    }

    /**
     * 安全执行一段对账步骤, 失败记录错误不阻断启动
     *
     * @param step 步骤名, 用于错误日志定位
     * @param block 对账逻辑
     */
    private fun runSafely(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.error("启动对账步骤失败: {}, 等待下次重启或变更自愈", step, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TaskQuartzReconciler::class.java)
    }
}

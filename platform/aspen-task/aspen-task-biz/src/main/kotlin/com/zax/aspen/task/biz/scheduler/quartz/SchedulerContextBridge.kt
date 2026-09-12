package com.zax.aspen.task.biz.scheduler.quartz

import com.zax.aspen.task.biz.housekeeping.LogRetentionService
import com.zax.aspen.task.biz.service.task.TaskDispatchHandler
import org.quartz.Scheduler
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

/**
 * 把调度侧处理器桥接进 Quartz Scheduler Context
 *
 * Quartz Job 实例由框架反射创建、不做 Spring 注入, 运行时经 Scheduler Context
 * 取回投递处理器与保留期清理服务。桥接必须先于调度器启动: Scheduler 是
 * SmartLifecycle (默认最高 phase), 停机恢复的 misfire 可能在调度器启动瞬间触发,
 * 若用 ApplicationRunner 桥接 (晚于调度器启动), 该窗口内触发的 Job 会拿到 null
 * 处理器而丢轮次 (2026-09-13 审核修复); 本组件以更低 phase 的 SmartLifecycle
 * 保证在任何调度触发发生前完成写入
 */
@Component
class SchedulerContextBridge(
    private val scheduler: Scheduler,
    private val dispatchHandler: TaskDispatchHandler,
    private val logRetentionService: LogRetentionService,
) : SmartLifecycle {
    @Volatile
    private var running: Boolean = false

    /**
     * 写入桥接键后标记运行, 先于调度器启动执行
     */
    override fun start() {
        val context = scheduler.context
        context[QuartzTaskKeys.CONTEXT_DISPATCH_HANDLER] = dispatchHandler
        context[QuartzTaskKeys.CONTEXT_LOG_RETENTION] = logRetentionService
        running = true
    }

    /**
     * 停止回调, 只清除运行标记
     */
    override fun stop() {
        running = false
    }

    /**
     * 是否已完成启动
     *
     * @return start 执行完成后为 true
     */
    override fun isRunning(): Boolean = running

    /**
     * 启动相位: 早于默认最高 phase 的调度器, 晚于普通 Bean 初始化
     *
     * @return 提前 1024 的相位值
     */
    override fun getPhase(): Int = SmartLifecycle.DEFAULT_PHASE - BRIDGE_PHASE_OFFSET

    private companion object {
        /** 相对调度器默认相位的提前量, 保证桥接先于一切调度触发 */
        const val BRIDGE_PHASE_OFFSET = 1024
    }
}

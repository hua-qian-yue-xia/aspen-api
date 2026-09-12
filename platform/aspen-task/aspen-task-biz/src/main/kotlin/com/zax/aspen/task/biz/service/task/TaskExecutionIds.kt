package com.zax.aspen.task.biz.service.task

import com.zax.aspen.task.api.enums.TaskTriggerSource
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 逻辑执行标识的构造规则
 *
 * 技术架构 14.1.3: 计划触发 taskId + scheduledFireTime + shard, 人工触发
 * taskId + requestId + shard; HTTP 投递模型中 shard 固定为 tenantId;
 * fireEpoch 取计划触发时间在部署时区下的毫秒值, 同一任务两次触发的时点必然
 * 不同, 保证同轮幂等、跨轮唯一
 */
object TaskExecutionIds {
    /**
     * 构造计划触发的逻辑执行标识
     *
     * @param definitionId 任务定义主键
     * @param fireTime 计划触发时间
     * @param tenantId 归属租户 (shard 段)
     * @return 形如 task-{definitionId}-f{epochMillis}-{tenantId} 的标识
     */
    fun scheduled(definitionId: Long, fireTime: LocalDateTime, tenantId: Long): String =
        "task-$definitionId-f${epochMillis(fireTime)}-$tenantId"

    /**
     * 构造人工触发的逻辑执行标识
     *
     * @param definitionId 任务定义主键
     * @param requestId 人工触发幂等键
     * @param tenantId 归属租户 (shard 段)
     * @return 形如 task-{definitionId}-m-{requestId}-{tenantId} 的标识
     */
    fun manual(definitionId: Long, requestId: String, tenantId: Long): String =
        "task-$definitionId-m-$requestId-$tenantId"

    /**
     * 按触发来源构造逻辑执行标识
     *
     * @param definitionId 任务定义主键
     * @param source 触发来源
     * @param fireTime 计划触发时间 (人工触发为其受理时间)
     * @param requestId 人工触发幂等键, 人工来源时必须非空
     * @param tenantId 归属租户
     * @return 逻辑执行唯一标识
     */
    fun build(
        definitionId: Long,
        source: TaskTriggerSource,
        fireTime: LocalDateTime,
        requestId: String?,
        tenantId: Long,
    ): String =
        when (source) {
            TaskTriggerSource.SCHEDULED -> scheduled(definitionId, fireTime, tenantId)
            TaskTriggerSource.MANUAL -> manual(definitionId, requireNotNull(requestId) { "人工触发缺少 requestId" }, tenantId)
        }

    /**
     * 计算时间在部署时区下的毫秒值
     *
     * @param time 业务本地时间
     * @return 毫秒时间戳
     */
    private fun epochMillis(time: LocalDateTime): Long =
        time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

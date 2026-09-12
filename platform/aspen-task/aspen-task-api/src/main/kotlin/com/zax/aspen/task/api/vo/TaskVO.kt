package com.zax.aspen.task.api.vo

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.task.api.enums.TaskConcurrentPolicy
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.api.enums.TaskMisfirePolicy
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerType
import java.time.LocalDateTime

/**
 * 任务定义的管理查询视图
 *
 * 管理端列表与详情的返回模型, 覆盖 task_definition 全部业务列与关键审计列;
 * tenantIds 在 tenantScope 为 SELECTED_TENANTS 时返回圈定清单, 否则为空列表;
 * 下次触发时间不落库 (Quartz 是运行时权威), 经 next-times 端点单独查询
 */
data class TaskVO(
    /** 数据库主键 */
    val definitionId: Long,
    /** 任务编码, 全局唯一且永久占用, 创建后不可修改 */
    val taskCode: String,
    /** 任务显示名 */
    val taskName: String,
    /** 任务说明 */
    val description: String?,
    /** 触发类型 */
    val triggerType: TaskTriggerType,
    /** CRON 表达式, 仅 CRON 类型非空 */
    val cronExpression: String?,
    /** 触发间隔秒数, 仅 FIXED_INTERVAL 类型非空 */
    val intervalSeconds: Int?,
    /** 一次性触发时点, 仅 ONE_TIME 类型非空 */
    val fireAt: LocalDateTime?,
    /** IANA 时区标识 */
    val timezoneId: String,
    /** 投递使用的 HTTP 方法 */
    val httpMethod: TaskHttpMethod,
    /** 目标地址 */
    val targetUrl: String,
    /** 自定义请求头键值对 */
    val headers: Map<String, String>,
    /** 请求体文本 */
    val body: String?,
    /** 单次投递的 HTTP 超时秒数 */
    val timeoutSeconds: Int,
    /** 含首次的总尝试次数上限 */
    val maxAttempts: Int,
    /** 失败重试的退避间隔秒数 */
    val backoffSeconds: Int,
    /** 租户圈定方式 */
    val tenantScope: TaskTenantScope,
    /** 圈定的租户清单, 仅 SELECTED_TENANTS 时非空 */
    val tenantIds: List<Long>,
    /** 错过触发策略 */
    val misfirePolicy: TaskMisfirePolicy,
    /** 并发策略 */
    val concurrentPolicy: TaskConcurrentPolicy,
    /** 任务负责人账号 */
    val ownerAccount: String,
    /** 启停状态 */
    val status: EnabledStatus,
    /** 乐观锁版本 */
    val version: Int,
    /** 创建时间 */
    val createdAt: LocalDateTime,
    /** 最近更新时间 */
    val updatedAt: LocalDateTime,
)

package com.zax.aspen.task.api.dto

import com.zax.aspen.task.api.enums.TaskConcurrentPolicy
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.api.enums.TaskMisfirePolicy
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 任务定义的新增或修改入参
 *
 * 管理端写入模型; Bean Validation 只兜字段形状, 触发字段与触发类型的匹配
 * (cron 必填于 CRON、间隔必填于 FIXED_INTERVAL、时点必填于 ONE_TIME)、时区合法
 * 与目标 URL 形状由 Task Service 校验; 修改时 taskCode 不可变更; tenantIds 仅在
 * tenantScope 为 SELECTED_TENANTS 时有意义, URL/头/体中的 {tenantId} 等占位符
 * 由投递器按当次执行的租户渲染
 */
data class TaskSaveDTO(
    /** 任务编码, 小写中划线格式, 全局唯一且永久占用, 创建后不可修改 */
    @field:NotBlank
    @field:Pattern(regexp = "[a-z][a-z0-9-]{1,63}")
    val taskCode: String,
    /** 任务显示名, 用于管理界面展示与搜索 */
    @field:NotBlank
    @field:Size(max = 100)
    val taskName: String,
    /** 任务说明, 描述任务业务语义与负责人协同信息; 可空 */
    @field:Size(max = 500)
    val description: String?,
    /** 触发类型, 决定触发字段的取舍 */
    @field:NotNull
    val triggerType: TaskTriggerType,
    /** CRON 表达式, Quartz 6/7 位制; 仅 triggerType 为 CRON 时必填 */
    @field:Size(max = 64)
    val cronExpression: String?,
    /** 触发间隔秒数, 不小于 1; 仅 triggerType 为 FIXED_INTERVAL 时必填 */
    @field:Min(1)
    val intervalSeconds: Int?,
    /** 一次性触发时点, 带时区的绝对时间; 仅 triggerType 为 ONE_TIME 时必填且须在未来 */
    val fireAt: LocalDateTime?,
    /** IANA 时区标识 (如 Asia/Shanghai), CRON 与时点解释的显式时区, 禁止容器默认时区 */
    @field:NotBlank
    @field:Size(max = 64)
    val timezoneId: String,
    /** 投递使用的 HTTP 方法 */
    @field:NotNull
    val httpMethod: TaskHttpMethod,
    /** 目标地址, 仅允许 http/https, 支持租户占位符 */
    @field:NotBlank
    @field:Size(max = 500)
    val targetUrl: String,
    /** 自定义请求头键值对, 外部目标的鉴权头在此配置; 值支持租户占位符; 键值长度由 Service 校验 */
    @field:Size(max = 20)
    val headers: Map<String, String> = emptyMap(),
    /**
     * 请求体文本, 原样透传, 仅 POST/PUT/PATCH 有意义; 支持租户占位符; 可空;
     * 上限 16000 字符按 4 字节字符预算落在 TEXT 列 64KB 之内, 超长在校验层
     * 拒绝而非落库时报错
     */
    @field:Size(max = 16_000)
    val body: String?,
    /** 单次投递的 HTTP 超时秒数 */
    @field:Min(1)
    @field:Max(300)
    val timeoutSeconds: Int = 30,
    /** 含首次的总尝试次数上限, 重试沿用同一逻辑执行 */
    @field:Min(1)
    @field:Max(10)
    val maxAttempts: Int = 1,
    /** 失败重试的退避间隔秒数, 0 表示立即重试 */
    @field:Min(0)
    @field:Max(86_400)
    val backoffSeconds: Int = 60,
    /** 租户圈定方式 */
    @field:NotNull
    val tenantScope: TaskTenantScope,
    /** 指定租户清单, 仅 tenantScope 为 SELECTED_TENANTS 时必填, 重复项由服务端去重; 上限 1000 防巨型清单 */
    @field:Size(max = 1000)
    val tenantIds: List<Long> = emptyList(),
    /** 错过触发策略 */
    @field:NotNull
    val misfirePolicy: TaskMisfirePolicy = TaskMisfirePolicy.FIRE_ONCE,
    /** 并发策略 */
    @field:NotNull
    val concurrentPolicy: TaskConcurrentPolicy = TaskConcurrentPolicy.SKIP,
    /** 任务负责人账号, 告警与审计归因 */
    @field:NotBlank
    @field:Size(max = 64)
    val ownerAccount: String,
)

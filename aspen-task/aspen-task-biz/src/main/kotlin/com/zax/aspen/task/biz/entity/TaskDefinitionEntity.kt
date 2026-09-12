package com.zax.aspen.task.biz.entity

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.task.api.enums.TaskConcurrentPolicy
import com.zax.aspen.task.api.enums.TaskHttpMethod
import com.zax.aspen.task.api.enums.TaskMisfirePolicy
import com.zax.aspen.task.api.enums.TaskTenantScope
import com.zax.aspen.task.api.enums.TaskTriggerType
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存统一任务的定义, 是任务调度的唯一权威源
 *
 * 典型场景: 管理端经 TaskApi 维护任务, Task Service 在写路径上「先同步 Quartz、
 * 后落本表」, 启动对账器按本表修复 QRTZ_ 运行时漂移; 任务是平台级调度资产,
 * 全体租户共享、不做租户隔离, 执行面的租户维度由 tenant_scope 与 task_tenant
 * 决定; 误删凭逻辑删除行恢复, 恢复后由对账器补建 Quartz 运行时
 */
@Entity
@Table(name = "task_definition")
interface TaskDefinitionEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val definitionId: Long

    /** 任务编码, 调用方与执行记录的稳定引用; 小写中划线格式, 全局唯一且永久占用, 创建后不可修改 */
    val taskCode: String

    /** 任务显示名, 用于管理界面展示与搜索 */
    val taskName: String

    /** 任务说明, 描述业务语义与负责人协同信息; 无说明时为 null */
    val description: String?

    /** 触发类型, 决定触发字段取舍与 Quartz Trigger 形态 */
    val triggerType: TaskTriggerType

    /** CRON 表达式, Quartz 6/7 位制; 仅 triggerType 为 CRON 时非空, 解释时区由 timezoneId 显式指定 */
    val cronExpression: String?

    /** 触发间隔秒数; 仅 triggerType 为 FIXED_INTERVAL 时非空 */
    val intervalSeconds: Int?

    /** 一次性触发时点, 带时区的绝对时间; 仅 triggerType 为 ONE_TIME 时非空, 触发后 Trigger 自然结束 */
    val fireAt: LocalDateTime?

    /** IANA 时区标识, CRON 与时点解释的显式时区, 禁止依赖容器默认时区 */
    val timezoneId: String

    /** 投递使用的 HTTP 方法 */
    val httpMethod: TaskHttpMethod

    /** 目标地址, 仅允许 http/https; 支持租户占位符, 投递前经目标校验 (SSRF 防护) */
    val targetUrl: String

    /** 自定义请求头键值对, 外部目标的鉴权头在此配置; 值支持租户占位符; 存取经 Jimmer @Serialized 与 JSON 列互转; 无自定义头时为 null */
    @Serialized
    val headers: Map<String, String>?

    /** 请求体文本, 原样透传, 仅 POST/PUT/PATCH 有意义; 支持租户占位符; 无请求体时为 null */
    val body: String?

    /** 单次投递的 HTTP 超时秒数 */
    @Default("30")
    val timeoutSeconds: Int

    /** 含首次的总尝试次数上限, 失败重试沿用同一逻辑执行并递增 attempt */
    @Default("1")
    val maxAttempts: Int

    /** 失败重试的退避间隔秒数, 0 表示立即重试 */
    @Default("60")
    val backoffSeconds: Int

    /** 租户圈定方式: 全部租户每次触发实时解析, 指定租户按 task_tenant 清单执行 */
    @Default("ALL_TENANTS")
    val tenantScope: TaskTenantScope

    /** 错过触发策略, 映射 Quartz Misfire 指令, 禁止默认行为 */
    @Default("FIRE_ONCE")
    val misfirePolicy: TaskMisfirePolicy

    /** 并发策略: 存在 RUNNING 执行时新触发的处置方式 */
    @Default("SKIP")
    val concurrentPolicy: TaskConcurrentPolicy

    /** 任务负责人账号, 告警与审计归因 */
    val ownerAccount: String

    /** 启停状态; disabled 的任务暂停触发, 执行记录保留 */
    @Default("ENABLED")
    val status: EnabledStatus
}

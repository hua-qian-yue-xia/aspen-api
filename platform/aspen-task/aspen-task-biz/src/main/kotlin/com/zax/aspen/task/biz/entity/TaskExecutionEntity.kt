package com.zax.aspen.task.biz.entity

import com.zax.aspen.common.database.model.AuditableEntity
import com.zax.aspen.common.database.model.VersionedEntity
import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskFailureKind
import com.zax.aspen.task.api.enums.TaskTriggerSource
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存逐租户的逻辑执行记录, 每租户每逻辑执行一条
 *
 * 典型场景: 每次触发达成「任务 × 租户」的执行展开——执行行先以 RUNNING 创建
 * (executionId 主键冲突即同逻辑执行已存在, 幂等跳过), HTTP 投递取得终态后回写;
 * 失败重试沿用同一 executionId 递增 attempt; 过程数据由保留期治理物理删除,
 * 不声明删除审计防止行膨胀
 */
@Entity
@Table(name = "task_execution")
interface TaskExecutionEntity : AuditableEntity, VersionedEntity {
    /** 逻辑执行唯一标识: 计划触发 task-{definitionId}-f{fireEpochMillis}-{tenantId}, 人工触发 task-{definitionId}-m-{requestId}-{tenantId} */
    @Id
    val executionId: String

    /** 所属任务定义主键快照 */
    val definitionId: Long

    /** 任务编码快照, 任务删除后执行记录仍可读 */
    val taskCode: String

    /** 本次执行归属的租户标识 (executionId 的 shard 段) */
    val tenantId: Long

    /** 触发来源: 计划触发或人工触发, 重试沿用原来源 */
    val triggerSource: TaskTriggerSource

    /** 人工触发幂等键, 同一 requestId 不会创建第二次逻辑执行; 计划触发为 null */
    val requestId: String?

    /** 计划触发时间, 人工触发为触发受理时间 */
    val fireTime: LocalDateTime

    /** 尝试轮次, 首次为 1, 失败重试递增 */
    @Default("1")
    val attempt: Int

    /** 执行状态: RUNNING 起始, SUCCESS/FAILED 终态, SKIPPED 为并发策略跳过 */
    @Default("RUNNING")
    val status: TaskExecutionStatus

    /** 失败类别, 细分超时/连接失败/HTTP 失败/目标拒绝/租户解析失败/实例中断; 仅 FAILED 时非空 */
    val failureKind: TaskFailureKind?

    /** 目标返回的 HTTP 状态码, 未取得响应 (超时/连接失败) 时为 null */
    val httpStatus: Int?

    /** 目标响应体片段, 截断存储 (默认 2000 字符) */
    val responseSnippet: String?

    /** 失败原因文本, 截断存储 */
    val errorMessage: String?

    /** 当前轮次投递开始时间 */
    val startedAt: LocalDateTime

    /** 当前轮次投递结束时间, 未结束时为 null */
    val finishedAt: LocalDateTime?

    /** 当前轮次耗时毫秒数 */
    val durationMs: Long?
}

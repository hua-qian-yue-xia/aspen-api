package com.zax.aspen.task.biz.entity

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.task.api.enums.TaskLogLevel
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存执行过程日志, 是执行走向的可观测载体
 *
 * 典型场景: 目标服务从投递请求的溯源请求头取得 executionId, 按步骤经内部回传
 * 契约上报; (execution_id, seq) 唯一约束使重复上报幂等跳过、乱序到达不破坏排序;
 * 不可变追加行, 与执行记录互不建外键, 保留期治理独立物理删除
 */
@Entity
@Table(name = "task_execution_log")
interface TaskExecutionLogEntity : CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val logId: Long

    /** 所属逻辑执行唯一标识, 与投递请求的 X-Aspen-Execution-Id 同值 */
    val executionId: String

    /** 执行内自增序号, 从 1 起, 调用方保证单调 */
    val seq: Int

    /** 日志级别 */
    @Default("INFO")
    val level: TaskLogLevel

    /** 日志消息文本 */
    val message: String

    /** 目标侧的日志产生时间; 调用方缺省时由平台按接收时间补齐 */
    val loggedAt: LocalDateTime
}

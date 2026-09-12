package com.zax.aspen.task.api.vo

import com.zax.aspen.task.api.enums.TaskLogLevel
import java.time.LocalDateTime

/**
 * 执行过程日志的管理查询视图
 *
 * 按逻辑执行的 seq 升序返回, 呈现一次执行的完整走向; created_at 与 logged_at
 * 分别是平台接收时间与目标侧产生时间, 差值可观测上报链路延迟
 */
data class TaskExecutionLogVO(
    /** 日志主键 */
    val logId: Long,
    /** 所属逻辑执行唯一标识 */
    val executionId: String,
    /** 执行内自增序号, 从 1 起 */
    val seq: Int,
    /** 日志级别 */
    val level: TaskLogLevel,
    /** 日志消息文本 */
    val message: String,
    /** 目标侧的日志产生时间 (缺省上报时为平台接收时间) */
    val loggedAt: LocalDateTime,
    /** 平台接收并落库的时间 */
    val createdAt: LocalDateTime,
)

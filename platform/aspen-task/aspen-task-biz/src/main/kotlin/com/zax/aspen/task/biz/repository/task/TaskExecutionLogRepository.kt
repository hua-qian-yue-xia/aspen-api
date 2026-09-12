package com.zax.aspen.task.biz.repository.task

import com.zax.aspen.task.api.dto.TaskExecutionLogEntryDTO
import com.zax.aspen.task.biz.entity.TaskExecutionLogEntity
import com.zax.aspen.task.biz.entity.TaskExecutionLogEntityDraft
import com.zax.aspen.task.biz.entity.createdAt
import com.zax.aspen.task.biz.entity.executionId
import com.zax.aspen.task.biz.entity.logId
import com.zax.aspen.task.biz.entity.seq
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.lt
import org.babyfish.jimmer.sql.kt.ast.expression.rowCount
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDateTime

/**
 * 访问执行过程日志表 task_execution_log
 *
 * 不可变追加行: (execution_id, seq) 唯一约束承载回传幂等, 重复上报的条目按
 * 唯一键冲突跳过; 保留期治理物理删除, 不声明更新与删除审计
 */
@Repository
class TaskExecutionLogRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 追加一批执行过程日志, 重复 (executionId, seq) 的条目幂等跳过
     *
     * @param executionId 所属逻辑执行唯一标识
     * @param entries 待追加的日志条目
     * @param loggedAtFallback 调用方未携带时间时的补齐时间
     * @param identity 写入审计列的系统身份
     * @return 实际落库的条数 (总条数减去幂等跳过的重复条数)
     */
    fun appendAll(
        executionId: String,
        entries: List<TaskExecutionLogEntryDTO>,
        loggedAtFallback: LocalDateTime,
        identity: String,
    ): Int {
        var appended = 0
        entries.forEach { entry ->
            try {
                sqlClient.entities.save(
                    TaskExecutionLogEntityDraft.`$`.produce {
                        this.executionId = executionId
                        seq = entry.seq
                        level = entry.level
                        message = entry.message
                        loggedAt = entry.loggedAt ?: loggedAtFallback
                        createdBy = identity
                    },
                ) {
                    setMode(SaveMode.INSERT_ONLY)
                }.modifiedEntity
                appended++
            } catch (e: Exception) {
                if (!e.isDuplicateKeyViolation()) {
                    throw e
                }
            }
        }
        return appended
    }

    /**
     * 统计逻辑执行已回传的日志条数, 供回传封顶校验
     *
     * 计数按 (execution_id, seq) 唯一键最左列扫描, 无需额外索引
     *
     * @param executionId 逻辑执行唯一标识
     * @return 已落库的日志条数
     */
    fun countByExecutionId(executionId: String): Long =
        sqlClient.createQuery(TaskExecutionLogEntity::class) {
            where(table.executionId eq executionId)
            select(rowCount())
        }.fetchOneOrNull() ?: 0L

    /**
     * 查询逻辑执行的过程日志, 按 seq 升序, 读取条数封顶
     *
     * 写路径已按单执行条数封顶, 读取同源封顶兜底配置放大下的无界读取
     *
     * @param executionId 逻辑执行唯一标识
     * @param limit 单执行读取条数上限
     * @return 过程日志实体列表, 无回传日志时返回空列表
     */
    fun findByExecutionId(executionId: String, limit: Int): List<TaskExecutionLogEntity> =
        sqlClient.createQuery(TaskExecutionLogEntity::class) {
            where(table.executionId eq executionId)
            orderBy(table.seq.asc())
            select(table)
        }.limit(limit).execute()

    /**
     * 查询创建时间早于截止线的日志主键, 供保留期清理分批物理删除
     *
     * @param cutoff 保留期截止线
     * @param limit 单批数量上限
     * @return 待删除的日志主键列表, 按创建时间升序
     */
    fun findExpiredIds(cutoff: LocalDateTime, limit: Int): List<Long> =
        sqlClient.createQuery(TaskExecutionLogEntity::class) {
            where(table.createdAt lt cutoff)
            select(table.logId)
        }.limit(limit).execute()

    /**
     * 按主键集合物理删除过程日志
     *
     * @param logIds 待删除的日志主键集合
     */
    fun deleteByIds(logIds: List<Long>) {
        if (logIds.isEmpty()) {
            return
        }
        sqlClient.createDelete(TaskExecutionLogEntity::class) {
            where(table.logId valueIn logIds)
        }.execute()
    }

    /**
     * 判断异常链中是否存在唯一键冲突
     *
     * @return 异常链中存在 DuplicateKeyException 或 SQLIntegrityConstraintViolationException 时为 `true`
     */
    private fun Exception.isDuplicateKeyViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any {
            it is DuplicateKeyException || it is SQLIntegrityConstraintViolationException
        }
}

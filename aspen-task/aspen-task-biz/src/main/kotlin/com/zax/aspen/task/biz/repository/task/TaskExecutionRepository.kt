package com.zax.aspen.task.biz.repository.task

import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.task.api.dto.TaskExecutionPageQuery
import com.zax.aspen.task.api.enums.TaskExecutionStatus
import com.zax.aspen.task.api.enums.TaskFailureKind
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.entity.TaskExecutionEntity
import com.zax.aspen.task.biz.entity.TaskExecutionEntityDraft
import com.zax.aspen.task.biz.entity.createdAt
import com.zax.aspen.task.biz.entity.definitionId
import com.zax.aspen.task.biz.entity.executionId
import com.zax.aspen.task.biz.entity.fireTime
import com.zax.aspen.task.biz.entity.requestId
import com.zax.aspen.task.biz.entity.status
import com.zax.aspen.task.biz.entity.tenantId
import com.zax.aspen.task.biz.entity.triggerSource
import com.zax.aspen.task.biz.entity.updatedAt
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ge
import org.babyfish.jimmer.sql.kt.ast.expression.le
import org.babyfish.jimmer.sql.kt.ast.expression.lt
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDateTime

/**
 * 访问执行记录表 task_execution
 *
 * 每租户每逻辑执行一条, execution_id 字符串主键承载幂等 (重复插入按 null 返回);
 * 状态回写携带 version 乐观锁, 竞争失败时由调用方忽略 (状态自然收敛)
 */
@Repository
class TaskExecutionRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按逻辑执行标识查找执行记录
     *
     * @param executionId 逻辑执行唯一标识
     * @return 匹配的执行实体, 不存在时返回 `null`
     */
    fun findById(executionId: String): TaskExecutionEntity? =
        sqlClient.createQuery(TaskExecutionEntity::class) {
            where(table.executionId eq executionId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 判断任务是否存在执行中的执行记录, 供并发策略检查
     *
     * @param definitionId 任务定义主键
     * @return 存在 RUNNING 执行时为 `true`
     */
    fun existsRunning(definitionId: Long): Boolean =
        sqlClient.createQuery(TaskExecutionEntity::class) {
            where(table.definitionId eq definitionId)
            where(table.status eq TaskExecutionStatus.RUNNING)
            select(table.executionId)
        }.limit(1).execute().isNotEmpty()

    /**
     * 判断同一人工触发幂等键是否已受理, 供手动触发预查重
     *
     * @param definitionId 任务定义主键
     * @param requestId 人工触发幂等键
     * @return 已存在同 requestId 执行记录时为 `true`
     */
    fun existsByRequestId(definitionId: Long, requestId: String): Boolean =
        sqlClient.createQuery(TaskExecutionEntity::class) {
            where(table.definitionId eq definitionId)
            where(table.requestId eq requestId)
            select(table.executionId)
        }.limit(1).execute().isNotEmpty()

    /**
     * 新增 RUNNING 执行记录
     *
     * execution_id 主键冲突视为同逻辑执行已存在, 返回 null 由调用方幂等跳过
     *
     * @param executionId 逻辑执行唯一标识
     * @param definition 所属任务定义, 提供定义主键与编码快照
     * @param tenantId 归属租户
     * @param triggerSource 触发来源
     * @param requestId 人工触发幂等键, 计划触发为 null
     * @param fireTime 计划触发时间
     * @param identity 写入审计列的系统身份
     * @return 落库后的执行实体, 同逻辑执行已存在时返回 `null`
     */
    fun insertRunning(
        executionId: String,
        definition: TaskDefinitionEntity,
        tenantId: Long,
        triggerSource: com.zax.aspen.task.api.enums.TaskTriggerSource,
        requestId: String?,
        fireTime: LocalDateTime,
        identity: String,
    ): TaskExecutionEntity? =
        try {
            sqlClient.entities.save(
                TaskExecutionEntityDraft.`$`.produce {
                    this.executionId = executionId
                    definitionId = definition.definitionId
                    taskCode = definition.taskCode
                    this.tenantId = tenantId
                    this.triggerSource = triggerSource
                    this.requestId = requestId
                    this.fireTime = fireTime
                    attempt = 1
                    status = TaskExecutionStatus.RUNNING
                    startedAt = LocalDateTime.now()
                    version = 1
                    createdBy = identity
                    updatedBy = identity
                },
            ) {
                setMode(SaveMode.INSERT_ONLY)
            }.modifiedEntity
        } catch (e: Exception) {
            if (e.isDuplicateKeyViolation()) {
                null
            } else {
                throw e
            }
        }

    /**
     * 新增 SKIPPED 执行记录, 并发策略跳过本轮时逐租户留痕
     *
     * @param executionId 逻辑执行唯一标识
     * @param definition 所属任务定义
     * @param tenantId 归属租户
     * @param triggerSource 触发来源
     * @param requestId 人工触发幂等键, 计划触发为 null
     * @param fireTime 计划触发时间
     * @param identity 写入审计列的系统身份
     */
    fun insertSkipped(
        executionId: String,
        definition: TaskDefinitionEntity,
        tenantId: Long,
        triggerSource: com.zax.aspen.task.api.enums.TaskTriggerSource,
        requestId: String?,
        fireTime: LocalDateTime,
        identity: String,
    ) {
        try {
            sqlClient.entities.save(
                TaskExecutionEntityDraft.`$`.produce {
                    this.executionId = executionId
                    definitionId = definition.definitionId
                    taskCode = definition.taskCode
                    this.tenantId = tenantId
                    this.triggerSource = triggerSource
                    this.requestId = requestId
                    this.fireTime = fireTime
                    attempt = 1
                    status = TaskExecutionStatus.SKIPPED
                    startedAt = LocalDateTime.now()
                    finishedAt = LocalDateTime.now()
                    durationMs = 0
                    errorMessage = "并发策略跳过: 上一轮执行仍在进行"
                    version = 1
                    createdBy = identity
                    updatedBy = identity
                },
            ) {
                setMode(SaveMode.INSERT_ONLY)
            }.modifiedEntity
        } catch (e: Exception) {
            if (!e.isDuplicateKeyViolation()) {
                throw e
            }
        }
    }

    /**
     * 回写执行终态 (成功或失败), 携带乐观锁
     *
     * @param execution 投递前的执行实体, 提供 executionId 与乐观锁 version
     * @param status 终态
     * @param failureKind 失败类别, 成功时为 null
     * @param httpStatus 目标返回的 HTTP 状态码, 未取得响应时为 null
     * @param responseSnippet 响应体片段, 已截断
     * @param errorMessage 失败原因文本
     * @param durationMs 本轮耗时毫秒
     * @param identity 写入审计列的系统身份
     * @return 更新后的执行实体, 乐观锁冲突时返回 `null`
     */
    fun complete(
        execution: TaskExecutionEntity,
        status: TaskExecutionStatus,
        failureKind: TaskFailureKind?,
        httpStatus: Int?,
        responseSnippet: String?,
        errorMessage: String?,
        durationMs: Long,
        identity: String,
    ): TaskExecutionEntity? =
        try {
            sqlClient.entities.save(
                TaskExecutionEntityDraft.`$`.produce {
                    executionId = execution.executionId
                    version = execution.version
                    this.status = status
                    this.failureKind = failureKind
                    this.httpStatus = httpStatus
                    this.responseSnippet = responseSnippet
                    this.errorMessage = errorMessage
                    finishedAt = LocalDateTime.now()
                    this.durationMs = durationMs
                    updatedBy = identity
                },
            ) {
                setMode(SaveMode.UPDATE_ONLY)
            }.modifiedEntity
        } catch (e: org.babyfish.jimmer.sql.exception.SaveException.OptimisticLockError) {
            null
        }

    /**
     * 把失败执行重新置为 RUNNING 并递增尝试轮次, 供重试派发
     *
     * @param execution 待重试的执行实体, 提供乐观锁 version
     * @param attempt 新的尝试轮次
     * @param identity 写入审计列的系统身份
     * @return 更新后的执行实体, 乐观锁冲突时返回 `null`
     */
    fun markRunning(execution: TaskExecutionEntity, attempt: Int, identity: String): TaskExecutionEntity? =
        try {
            sqlClient.entities.save(
                TaskExecutionEntityDraft.`$`.produce {
                    executionId = execution.executionId
                    version = execution.version
                    this.attempt = attempt
                    status = TaskExecutionStatus.RUNNING
                    startedAt = LocalDateTime.now()
                    finishedAt = null
                    durationMs = null
                    failureKind = null
                    httpStatus = null
                    responseSnippet = null
                    errorMessage = null
                    updatedBy = identity
                },
            ) {
                setMode(SaveMode.UPDATE_ONLY)
            }.modifiedEntity
        } catch (e: org.babyfish.jimmer.sql.exception.SaveException.OptimisticLockError) {
            null
        }

    /**
     * 把僵尸 RUNNING 执行置为实例中断终态
     *
     * @param execution 待回收的执行实体, 提供乐观锁 version
     * @param identity 写入审计列的系统身份
     * @return 更新后的执行实体, 乐观锁冲突时返回 `null`
     */
    fun markInterrupted(execution: TaskExecutionEntity, identity: String): TaskExecutionEntity? =
        try {
            sqlClient.entities.save(
                TaskExecutionEntityDraft.`$`.produce {
                    executionId = execution.executionId
                    version = execution.version
                    status = TaskExecutionStatus.FAILED
                    failureKind = TaskFailureKind.INTERRUPTED
                    errorMessage = "投递实例中断, 由对账回收"
                    finishedAt = LocalDateTime.now()
                    updatedBy = identity
                },
            ) {
                setMode(SaveMode.UPDATE_ONLY)
            }.modifiedEntity
        } catch (e: org.babyfish.jimmer.sql.exception.SaveException.OptimisticLockError) {
            null
        }

    /**
     * 分页查询执行记录
     *
     * @param query 分页与过滤条件
     * @param pageSize 经 DatabaseLimits 校验后的单页数量
     * @return 执行实体分页结果, 按定义与计划触发时间倒序
     */
    fun page(query: TaskExecutionPageQuery, pageSize: Int): PageResult<TaskExecutionEntity> {
        val paged = sqlClient.createQuery(TaskExecutionEntity::class) {
            query.definitionId?.let { where(table.definitionId eq it) }
            query.tenantId?.let { where(table.tenantId eq it) }
            query.status?.let { where(table.status eq it) }
            query.triggerSource?.let { where(table.triggerSource eq it) }
            query.fireTimeFrom?.let { where(table.fireTime ge it) }
            query.fireTimeTo?.let { where(table.fireTime le it) }
            orderBy(table.definitionId.desc(), table.fireTime.desc())
            select(table)
        }.fetchPage(query.pageNumber - 1, pageSize)
        return PageResult(
            items = paged.rows,
            totalElements = paged.totalRowCount,
            pageNumber = query.pageNumber,
            pageSize = pageSize,
        )
    }

    /**
     * 查询创建时间早于截止线的执行记录 id, 供保留期清理分批物理删除
     *
     * @param cutoff 保留期截止线
     * @param limit 单批数量上限
     * @return 待删除的执行 id 列表, 按创建时间升序
     */
    fun findExpiredIds(cutoff: LocalDateTime, limit: Int): List<String> =
        sqlClient.createQuery(TaskExecutionEntity::class) {
            where(table.createdAt lt cutoff)
            orderBy(table.createdAt.asc())
            select(table.executionId)
        }.limit(limit).execute()

    /**
     * 查询超过回收窗口仍未回写的 RUNNING 执行, 供僵尸运行态回收
     *
     * @param cutoff 回收窗口截止线 (updated_at 早于该时间)
     * @param limit 单批数量上限
     * @return 疑似实例中断的执行实体列表
     */
    fun findStaleRunning(cutoff: LocalDateTime, limit: Int): List<TaskExecutionEntity> =
        sqlClient.createQuery(TaskExecutionEntity::class) {
            where(table.status eq TaskExecutionStatus.RUNNING)
            where(table.updatedAt lt cutoff)
            orderBy(table.updatedAt.asc())
            select(table)
        }.limit(limit).execute()

    /**
     * 按执行 id 集合物理删除执行记录
     *
     * @param executionIds 待删除的执行 id 集合
     */
    fun deleteByIds(executionIds: List<String>) {
        if (executionIds.isEmpty()) {
            return
        }
        sqlClient.createDelete(TaskExecutionEntity::class) {
            where(table.executionId valueIn executionIds)
        }.execute()
    }

    /**
     * 判断异常链中是否存在唯一键冲突
     *
     * Jimmer 经自身执行器抛出的约束冲突不总是被 Spring 翻译为 DuplicateKeyException,
     * 需要沿 cause 链同时识别 Spring 翻译异常与 JDBC 原生异常
     *
     * @return 异常链中存在 DuplicateKeyException 或 SQLIntegrityConstraintViolationException 时为 `true`
     */
    private fun Exception.isDuplicateKeyViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any {
            it is DuplicateKeyException || it is SQLIntegrityConstraintViolationException
        }
}

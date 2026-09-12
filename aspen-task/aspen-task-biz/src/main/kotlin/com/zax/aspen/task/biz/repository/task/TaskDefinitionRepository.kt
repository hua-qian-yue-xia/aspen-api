package com.zax.aspen.task.biz.repository.task

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.task.api.dto.TaskPageQuery
import com.zax.aspen.task.api.dto.TaskSaveDTO
import com.zax.aspen.task.biz.entity.TaskDefinitionEntity
import com.zax.aspen.task.biz.entity.TaskDefinitionEntityDraft
import com.zax.aspen.task.biz.entity.definitionId
import com.zax.aspen.task.biz.entity.status
import com.zax.aspen.task.biz.entity.taskCode
import com.zax.aspen.task.biz.entity.taskName
import com.zax.aspen.task.biz.entity.triggerType
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.like
import org.springframework.stereotype.Repository

/**
 * 访问任务定义表 task_definition
 *
 * 逻辑删除由 Jimmer 按 deleted_at 自动过滤, 查询无需手工排除已删除行;
 * 任务定义是唯一权威, Quartz 同步由 Service 层编排
 */
@Repository
class TaskDefinitionRepository(
    private val sqlClient: KSqlClient,
) {
    /**
     * 按主键查找未删除任务
     *
     * @param definitionId 任务定义表主键 id
     * @return 匹配的任务实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findById(definitionId: Long): TaskDefinitionEntity? =
        sqlClient.createQuery(TaskDefinitionEntity::class) {
            where(table.definitionId eq definitionId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 按编码查找未删除任务
     *
     * @param taskCode 任务编码, 全局唯一且永久占用
     * @return 匹配的任务实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByCode(taskCode: String): TaskDefinitionEntity? =
        sqlClient.createQuery(TaskDefinitionEntity::class) {
            where(table.taskCode eq taskCode)
            select(table)
        }.fetchOneOrNull()

    /**
     * 查询全部启用任务, 供启动对账器同步 Quartz 运行时
     *
     * @return 启用状态的任务实体列表, 停用或已删除的任务不在其中
     */
    fun findAllEnabled(): List<TaskDefinitionEntity> =
        sqlClient.createQuery(TaskDefinitionEntity::class) {
            where(table.status eq EnabledStatus.ENABLED)
            select(table)
        }.execute()

    /**
     * 分页查询任务定义
     *
     * @param query 分页与过滤条件, 条件为空时返回全量分页
     * @param pageSize 经 DatabaseLimits 校验后的单页数量
     * @return 任务实体分页结果, 按主键倒序
     */
    fun page(query: TaskPageQuery, pageSize: Int): PageResult<TaskDefinitionEntity> {
        val paged = sqlClient.createQuery(TaskDefinitionEntity::class) {
            query.taskCode?.takeIf { it.isNotBlank() }?.let { where(table.taskCode like "%$it%") }
            query.taskName?.takeIf { it.isNotBlank() }?.let { where(table.taskName like "%$it%") }
            query.status?.let { where(table.status eq it) }
            query.triggerType?.let { where(table.triggerType eq it) }
            orderBy(table.definitionId.desc())
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
     * 新增任务, 显式 INSERT_ONLY: 无业务键的新对象不接受默认 upsert 语义
     *
     * @param command 任务新增入参, 全部业务列取自该对象, version 固定写 1
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的任务实体, 含数据库生成的 id
     */
    fun insert(command: TaskSaveDTO, identity: String): TaskDefinitionEntity =
        sqlClient.entities.save(
            TaskDefinitionEntityDraft.`$`.produce {
                fillBusinessColumns(command)
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 按乐观锁更新任务业务列
     *
     * 携带既有 version 触发 Jimmer 乐观锁校验, 并发冲突抛出 OptimisticLockError;
     * updated_at 由审计拦截器写入, 此处只负责操作人
     *
     * @param existing 修改前的任务实体, 提供 definitionId 与乐观锁 version
     * @param command 任务修改入参, 全部业务列以该对象覆盖
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的任务实体, version 已自增
     */
    fun update(existing: TaskDefinitionEntity, command: TaskSaveDTO, identity: String): TaskDefinitionEntity =
        sqlClient.entities.save(
            TaskDefinitionEntityDraft.`$`.produce {
                definitionId = existing.definitionId
                version = existing.version
                fillBusinessColumns(command)
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 按乐观锁仅更新任务启停状态
     *
     * @param existing 修改前的任务实体, 提供 definitionId 与乐观锁 version
     * @param status 目标启停状态
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的任务实体, version 已自增
     */
    fun updateStatus(existing: TaskDefinitionEntity, status: EnabledStatus, identity: String): TaskDefinitionEntity =
        sqlClient.entities.save(
            TaskDefinitionEntityDraft.`$`.produce {
                definitionId = existing.definitionId
                version = existing.version
                this.status = status
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.UPDATE_ONLY)
        }.modifiedEntity

    /**
     * 逻辑删除任务, deleted_at 由 Jimmer 写入当前时间, 删除人审计留空
     *
     * @param existing 待删除的任务实体, 仅取其 definitionId 定位行
     */
    fun delete(existing: TaskDefinitionEntity) {
        sqlClient.entities.delete(TaskDefinitionEntity::class, existing.definitionId)
    }

    /**
     * 把保存入参填充到草稿的业务列
     *
     * @param command 任务新增或修改入参
     */
    private fun TaskDefinitionEntityDraft.fillBusinessColumns(command: TaskSaveDTO) {
        taskCode = command.taskCode
        taskName = command.taskName
        description = command.description
        triggerType = command.triggerType
        cronExpression = command.cronExpression
        intervalSeconds = command.intervalSeconds
        fireAt = command.fireAt
        timezoneId = command.timezoneId
        httpMethod = command.httpMethod
        targetUrl = command.targetUrl
        headers = command.headers.takeIf { it.isNotEmpty() }
        body = command.body
        timeoutSeconds = command.timeoutSeconds
        maxAttempts = command.maxAttempts
        backoffSeconds = command.backoffSeconds
        tenantScope = command.tenantScope
        misfirePolicy = command.misfirePolicy
        concurrentPolicy = command.concurrentPolicy
        ownerAccount = command.ownerAccount
        status = EnabledStatus.ENABLED
    }
}

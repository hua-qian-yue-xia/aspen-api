package com.zax.aspen.task.biz.repository.task

import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.task.biz.entity.TaskTenantEntity
import com.zax.aspen.task.biz.entity.TaskTenantEntityDraft
import com.zax.aspen.task.biz.entity.definitionId
import com.zax.aspen.task.biz.entity.tenantId
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.stereotype.Repository

/**
 * 访问指定租户圈定清单 task_tenant
 *
 * 不可变关系行, 清单变更由整体替换实现 (先删后插); 事务边界只在 Service 公开
 * 方法 (7.6), 本类的替换由任务保存事务包裹保证原子, 清单规模由入参校验
 * 封顶 (≤1000, TaskSaveDTO @Size), 插入按批处理上限分批防巨型单语句
 */
@Repository
class TaskTenantRepository(
    private val sqlClient: KSqlClient,
    private val databaseLimits: DatabaseLimits,
) {
    /**
     * 查询任务圈定的租户清单
     *
     * @param definitionId 所属任务定义主键
     * @return 圈定的租户标识列表, 按租户 id 升序, 未圈定时返回空列表
     */
    fun findTenantIds(definitionId: Long): List<Long> =
        sqlClient.createQuery(TaskTenantEntity::class) {
            where(table.definitionId eq definitionId)
            orderBy(table.tenantId.asc())
            select(table.tenantId)
        }.execute()

    /**
     * 整体替换任务的圈定租户清单, 按批处理上限分批插入
     *
     * 事务由 Service 的任务保存事务提供 (先删后插原子), 本方法不自带事务边界;
     * 分批提交在同一外层事务内只是分次发语句, 不破坏原子性
     *
     * @param definitionId 所属任务定义主键
     * @param tenantIds 新的租户标识清单, 由 Service 去重排序并校验上限后传入
     * @param identity 操作人身份标识, 写入 createdBy 审计列
     */
    fun replaceAll(definitionId: Long, tenantIds: List<Long>, identity: String) {
        deleteByDefinitionId(definitionId)
        if (tenantIds.isEmpty()) {
            return
        }
        tenantIds.chunked(databaseLimits.maxBatchSize).forEach { batch ->
            sqlClient.entities.saveEntities(
                batch.map { tenantId ->
                    TaskTenantEntityDraft.`$`.produce {
                        this.definitionId = definitionId
                        this.tenantId = tenantId
                        createdBy = identity
                    }
                },
            ) {
                setMode(SaveMode.INSERT_ONLY)
            }
        }
    }

    /**
     * 物理删除任务的全部圈定行, 任务删除时随任务清理
     *
     * @param definitionId 所属任务定义主键
     */
    fun deleteByDefinitionId(definitionId: Long) {
        sqlClient.createDelete(TaskTenantEntity::class) {
            where(table.definitionId eq definitionId)
        }.execute()
    }

    /**
     * 批量查询多个任务圈定的租户标识
     *
     * @param definitionIds 任务定义主键集合
     * @return 匹配的圈定关系实体列表
     */
    fun findByDefinitionIds(definitionIds: List<Long>): List<TaskTenantEntity> =
        sqlClient.createQuery(TaskTenantEntity::class) {
            where(table.definitionId valueIn definitionIds)
            orderBy(table.definitionId.asc(), table.tenantId.asc())
            select(table)
        }.execute()
}

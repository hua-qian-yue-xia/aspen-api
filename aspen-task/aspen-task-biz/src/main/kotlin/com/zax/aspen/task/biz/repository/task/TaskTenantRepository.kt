package com.zax.aspen.task.biz.repository.task

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
import org.springframework.transaction.annotation.Transactional

/**
 * 访问指定租户圈定清单 task_tenant
 *
 * 不可变关系行, 清单变更由整体替换实现 (先删后插), 与任务保存同事务提交
 */
@Repository
class TaskTenantRepository(
    private val sqlClient: KSqlClient,
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
     * 整体替换任务的圈定租户清单
     *
     * @param definitionId 所属任务定义主键
     * @param tenantIds 新的租户标识清单, 由 Service 去重排序后传入
     * @param identity 操作人身份标识, 写入 createdBy 审计列
     */
    @Transactional
    fun replaceAll(definitionId: Long, tenantIds: List<Long>, identity: String) {
        deleteByDefinitionId(definitionId)
        if (tenantIds.isEmpty()) {
            return
        }
        sqlClient.entities.saveEntities(
            tenantIds.map { tenantId ->
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

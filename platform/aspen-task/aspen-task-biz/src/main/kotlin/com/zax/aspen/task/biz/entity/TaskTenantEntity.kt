package com.zax.aspen.task.biz.entity

import com.zax.aspen.common.database.model.CreateAuditEntity
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存指定租户圈定任务的多选租户清单
 *
 * 典型场景: tenant_scope 为 SELECTED_TENANTS 的任务在每次触发时按本表圈定执行面,
 * 清单变更由 Service 在任务保存事务内整体替换 (先删后插); 行不可变、物理删除,
 * 任务删除时随任务一并清理; 租户标识是跨库普通列 (对齐 Storage 惯例), 租户
 * 合法性由投递前的租户解析与目标服务的 fail-closed 过滤器保证
 */
@Entity
@Table(name = "task_tenant")
interface TaskTenantEntity : CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val taskTenantId: Long

    /** 所属任务定义; 不声明对象关联, 清单由 Service 按定义整体读写 */
    val definitionId: Long

    /** 圈定的租户标识, 与 definition_id 联合唯一 */
    val tenantId: Long
}

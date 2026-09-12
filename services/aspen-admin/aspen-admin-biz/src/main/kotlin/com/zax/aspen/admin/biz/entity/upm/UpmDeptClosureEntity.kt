package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table

/**
 * 保存部门祖先与后代闭包路径以支持范围查询
 *
 * 典型场景: 数据权限「本部门及下级」的展开、组织树任意层级的祖先后代查询;
 * 部门移动时由 Service 全量重建受影响路径, 每对祖先后代恰好一行
 */
@Entity
@Table(name = "upm_dept_closure")
interface UpmDeptClosureEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val deptClosureId: Long

    /** 祖先部门; 包含自身作为 depth 为 0 的行 */
    @ManyToOne
    @JoinColumn(name = "ancestor_id", referencedColumnName = "dept_id")
    val ancestor: UpmDeptEntity

    /** 祖先部门主键; ancestor 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("ancestor")
    val ancestorId: Long

    /** 后代部门 */
    @ManyToOne
    @JoinColumn(name = "descendant_id", referencedColumnName = "dept_id")
    val descendant: UpmDeptEntity

    /** 后代部门主键; descendant 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("descendant")
    val descendantId: Long

    /** 祖先到后代的跳数; 自身为 0, 直接子部门为 1; 按层级过滤时使用 */
    val depth: Int
}

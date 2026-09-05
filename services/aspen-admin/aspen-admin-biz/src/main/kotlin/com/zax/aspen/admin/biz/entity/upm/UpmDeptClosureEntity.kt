package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存部门祖先与后代闭包路径以支持范围查询 */
@Entity
@Table(name = "upm_dept_closure")
interface UpmDeptClosureEntity : UpmCreationEntity {
    @Column(name = "ancestor_id")
    val ancestorId: Long

    @Column(name = "descendant_id")
    val descendantId: Long

    @Column(name = "depth")
    val depth: Int
}

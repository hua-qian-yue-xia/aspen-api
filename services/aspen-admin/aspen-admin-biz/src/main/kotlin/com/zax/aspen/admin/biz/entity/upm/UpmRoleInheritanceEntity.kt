package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存角色继承闭包路径以支持多层权限组合 */
@Entity
@Table(name = "upm_role_inheritance")
interface UpmRoleInheritanceEntity : UpmCreationEntity {
    @Column(name = "parent_role_id")
    val parentRoleId: Long

    @Column(name = "child_role_id")
    val childRoleId: Long

    @Column(name = "depth")
    val depth: Int
}

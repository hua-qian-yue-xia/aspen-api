package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存角色自定义部门数据范围 */
@Entity
@Table(name = "upm_role_dept")
interface UpmRoleDeptEntity : UpmCreationEntity {
    @Column(name = "role_id")
    val roleId: Long

    @Column(name = "dept_id")
    val deptId: Long

    @Column(name = "include_children")
    @Default("true")
    val includeChildren: Boolean
}

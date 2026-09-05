package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存菜单或按钮到后端权限的绑定 */
@Entity
@Table(name = "upm_menu_permission")
interface UpmMenuPermissionEntity : UpmCreationEntity {
    @Column(name = "menu_id")
    val menuId: Long

    @Column(name = "permission_id")
    val permissionId: Long

    @Column(name = "relation_type")
    @Default("required")
    val relationType: String
}

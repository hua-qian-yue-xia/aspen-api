package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存角色到菜单的导航授权, 不替代后端权限校验 */
@Entity
@Table(name = "upm_role_menu")
interface UpmRoleMenuEntity : UpmCreationEntity {
    @Column(name = "role_id")
    val roleId: Long

    @Column(name = "menu_id")
    val menuId: Long

    @Column(name = "grant_source")
    @Default("manual")
    val grantSource: String

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?
}

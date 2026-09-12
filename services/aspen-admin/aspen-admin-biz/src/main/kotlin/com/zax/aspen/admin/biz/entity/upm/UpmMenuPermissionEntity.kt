package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.database.model.CreateAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Table

/**
 * 保存菜单或按钮到后端权限的绑定
 *
 * 典型场景: 前端按钮级权限控制; 菜单可见不等于接口有权, 按钮操作前按本表校验后端权限
 */
@Entity
@Table(name = "upm_menu_permission")
interface UpmMenuPermissionEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val menuPermissionId: Long

    /** 绑定菜单; button 类型的菜单项绑定后端权限, 菜单删除时级联删除绑定 */
    @ManyToOne
    @JoinColumn(name = "menu_id", referencedColumnName = "menu_id")
    val menu: UpmMenuEntity

    /** 菜单主键; menu 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("menu")
    val menuId: Long

    /** 绑定的权限; 权限删除时级联删除绑定 */
    @ManyToOne
    @JoinColumn(name = "permission_id", referencedColumnName = "permission_id")
    val permission: UpmPermissionEntity

    /** 权限主键; permission 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("permission")
    val permissionId: Long

    /** 绑定关系类型, 约定取值为 required/optional; required 表示进入该页面前端必须具备该权限 */
    @Default("required")
    val relationType: String
}

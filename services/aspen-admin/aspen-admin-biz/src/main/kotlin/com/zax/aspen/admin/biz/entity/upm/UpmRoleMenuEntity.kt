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
import java.time.LocalDateTime

/**
 * 保存角色到菜单的导航授权
 *
 * 典型场景: 角色菜单树渲染; 只控制导航可见性, 后端接口访问以权限表为准
 */
@Entity
@Table(name = "upm_role_menu")
interface UpmRoleMenuEntity : TenantScopedEntity, CreateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val roleMenuId: Long

    /** 授权角色; 角色删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "role_id")
    val role: UpmRoleEntity

    /** 授权角色主键; role 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("role")
    val roleId: Long

    /** 授权菜单; 菜单删除时级联删除授权 */
    @ManyToOne
    @JoinColumn(name = "menu_id", referencedColumnName = "menu_id")
    val menu: UpmMenuEntity

    /** 授权菜单主键; menu 关联的标量视图, 按主键过滤与保存时使用 */
    @IdView("menu")
    val menuId: Long

    /** 授权来源, 约定取值为 manual/template, 模板批量分配时标记 template */
    @Default("manual")
    val grantSource: String

    /** 授权生效起点; 为空表示立即生效 */
    val validFrom: LocalDateTime?

    /** 授权失效终点; 到期后菜单不再可见; 为空表示长期有效 */
    val validTo: LocalDateTime?
}

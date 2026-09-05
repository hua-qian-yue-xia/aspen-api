package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table

/** 保存目录、页面、按钮和外部链接的导航元数据 */
@Entity
@Table(name = "upm_menu")
interface UpmMenuEntity : UpmMutableEntity {
    @Column(name = "parent_id")
    val parentId: Long?

    @Column(name = "menu_code")
    val menuCode: String

    @Column(name = "platform")
    @Default("admin")
    val platform: String

    @Column(name = "menu_type")
    @Default("page")
    val menuType: String

    @Column(name = "name")
    val name: String

    @Column(name = "route_name")
    val routeName: String?

    @Column(name = "route_path")
    val routePath: String?

    @Column(name = "component")
    val component: String?

    @Column(name = "redirect")
    val redirect: String?

    @Column(name = "icon")
    val icon: String?

    @Column(name = "active_menu")
    val activeMenu: String?

    @Column(name = "external_url")
    val externalUrl: String?

    @Column(name = "open_target")
    @Default("self")
    val openTarget: String

    @Column(name = "ancestor_path")
    @Default("/")
    val ancestorPath: String

    @Column(name = "level")
    @Default("0")
    val level: Int

    @Column(name = "is_visible")
    @Default("true")
    val isVisible: Boolean

    @Column(name = "is_enabled")
    @Default("true")
    val isEnabled: Boolean

    @Column(name = "is_cached")
    @Default("false")
    val isCached: Boolean

    @Column(name = "is_affixed")
    @Default("false")
    val isAffixed: Boolean

    @Column(name = "show_breadcrumb")
    @Default("true")
    val showBreadcrumb: Boolean

    @Column(name = "hide_children")
    @Default("false")
    val hideChildren: Boolean

    @Column(name = "sort_order")
    @Default("0")
    val sortOrder: Int

    @Serialized
    @Column(name = "query_parameters")
    val queryParameters: Map<String, Any?>?

    @Serialized
    @Column(name = "route_metadata")
    val routeMetadata: Map<String, Any?>?

    @Column(name = "status")
    @Default("enabled")
    val status: String
}

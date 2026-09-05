package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table

/**
 * 保存目录、页面、按钮和外部链接的导航元数据
 *
 * 典型场景: 前端管理界面的动态路由与菜单树渲染;
 * 菜单只决定导航可见性, 后端访问控制以权限表为准, 两者不能混用
 */
@Entity
@Table(name = "upm_menu")
interface UpmMenuEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val menuId: Long

    /** 父菜单主键; 为空表示顶级菜单; 移动菜单时由 Service 同步重建路径 */
    val parentId: Long?

    /** 菜单编码, 同租户同平台唯一; 前端路由与权限点引用的稳定标识 */
    val menuCode: String

    /** 所属平台, 约定取值为 admin/app 等; 同一编码可在不同平台复用 */
    @Default("admin")
    val platform: String

    /** 菜单类型, 约定取值为 catalog/page/button/link; button 类型只承载权限点不参与路由 */
    @Default("page")
    val menuType: String

    /** 菜单显示名, 菜单树与面包屑展示 */
    val name: String

    /** 前端路由名称; page 类型必填, 与前端路由注册名一致 */
    val routeName: String?

    /** 前端路由路径, 例如 /system/user; page 类型必填 */
    val routePath: String?

    /** 页面组件路径, 前端懒加载组件的定位; catalog 类型为空 */
    val component: String?

    /** 进入菜单时的重定向目标, 常用于目录默认跳转到第一个子页面 */
    val redirect: String?

    /** 菜单图标标识, 前端图标库名称 */
    val icon: String?

    /** 激活态菜单标识, 详情页高亮所属菜单时使用 */
    val activeMenu: String?

    /** 外部链接地址; menuType 为 link 时点击跳转 */
    val externalUrl: String?

    /** 链接打开方式, 约定取值为 self/blank */
    @Default("self")
    val openTarget: String

    /** 祖先路径, 例如 /1/3/9/; 冗余加速面包屑与层级查询 */
    @Default("/")
    val ancestorPath: String

    /** 层级深度, 顶级为 0; 树缩进展示使用 */
    @Default("0")
    val level: Int

    /** 菜单可见标记; false 时不在导航出现但路由仍可访问, 用于隐藏的详情页 */
    @Default("true")
    val isVisible: Boolean

    /** 菜单启用标记; false 时路由与导航同时下线 */
    @Default("true")
    val isEnabled: Boolean

    /** 页面缓存标记; 前端 keep-alive 使用, 开启后切换回来保留列表状态 */
    @Default("false")
    val isCached: Boolean

    /** 固定在标签栏标记; 开启后该页签不可关闭 */
    @Default("false")
    val isAffixed: Boolean

    /** 面包屑展示标记; false 时该层级不出现在面包屑中 */
    @Default("true")
    val showBreadcrumb: Boolean

    /** 隐藏子菜单标记; 目录只有单子页时直接展示子页使用 */
    @Default("false")
    val hideChildren: Boolean

    /** 同级展示顺序, 升序排列 */
    @Default("0")
    val sortOrder: Int

    /** 路由查询参数, 序列化键值对; 带默认参数打开页面时使用 */
    @Serialized
    val queryParameters: Map<String, Any?>?

    /** 路由附加元数据, 例如页面标题、权限前缀; 前端路由注册时合并 */
    @Serialized
    val routeMetadata: Map<String, Any?>?

    /** 菜单状态; 与 isEnabled 语义分工: status 是业务启停, isEnabled 是导航开关 */
    @Default("ENABLED")
    val status: EnabledStatus
}

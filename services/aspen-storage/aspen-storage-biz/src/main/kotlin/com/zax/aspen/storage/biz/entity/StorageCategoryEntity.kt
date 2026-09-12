package com.zax.aspen.storage.biz.entity

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.OrderedProp
import org.babyfish.jimmer.sql.Table

/**
 * 保存租户级文件业务分类的树形定义, 与 FileType 技术形态正交
 *
 * 典型场景: 运营按业务用途自建分类树 (如「营销物料/商品图」「交付物/合同」),
 * 上传方按 categoryCode 选填挂载到任意层级节点, 文件未挂载即为未分类;
 * 子树查询走 (tenant_id, parent_id) 索引加 MySQL 递归 CTE, 树一致性
 * (成环、跨租户挂父、删除前置校验) 由 Service 维护; 分类是租户业务数据,
 * 建删重建是常态, 同码重建依赖 IFNULL 哨兵唯一键放行
 */
@Entity
@Table(name = "storage_category")
interface StorageCategoryEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val categoryId: Long

    /** 父分类, 根节点为空; 不设数据库外键, 移动子树时由 Service 在一个事务内校验目标不是自身后代, 防止成环 */
    @ManyToOne
    @JoinColumn(name = "parent_id", referencedColumnName = "category_id")
    val parent: StorageCategoryEntity?

    /** 父分类主键; parent 关联的标量视图, 树形选择器逐级加载与移动校验使用 */
    @IdView("parent")
    val parentId: Long?

    /** 分类编码, 上传方以其稳定引用分类; 小写中划线格式, 同租户内唯一, 创建后不可修改, 逻辑删除后可重建同码 */
    val categoryCode: String

    /** 分类显示名, 用于管理界面与选择器展示, 可随时修改不影响已挂载文件 */
    val categoryName: String

    /** 同级展示顺序, 升序排列; 默认 0 表示按创建顺序兜底 */
    @Default("0")
    val sortOrder: Int

    /** 分类启停状态; disabled 后不出现在选择器且新文件禁止挂载, 已挂载文件不受影响 */
    @Default("ENABLED")
    val status: EnabledStatus

    /**
     * 直接子分类; 树形选择器级联逐级加载使用, 与 sys_dict_item.children 同款有界集合;
     * 声明 sortOrder 升序、categoryId 升序兜底的确定性排序, 同级展示顺序不依赖数据库返回顺序
     */
    @OneToMany(mappedBy = "parent", orderedProps = [OrderedProp("sortOrder"), OrderedProp("categoryId")])
    val children: List<StorageCategoryEntity>
}

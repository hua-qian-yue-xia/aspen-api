package com.zax.aspen.admin.biz.entity.sys

import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.database.model.TenantScopedEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存字典项的值、显示文本、层级关系和前端展示属性
 *
 * 典型场景: 下拉选项的 value/label 来源、详情页把存储值翻译为中文文本;
 * 省市区等树形字典通过 parentId 组织级联, 扁平字典 parentId 为空
 */
@Entity
@Table(name = "sys_dict_item")
interface SysDictItemEntity : TenantScopedEntity, MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val dictItemId: Long

    /** 所属字典主键; 字典物理删除时随级联删除全部字典项 */
    val dictId: Long

    /** 父字典项主键, 支持省市区等树形级联选择; 扁平字典为空; 不设数据库外键, 移动子树时由 Service 在一个事务内维护层级一致 */
    val parentId: Long?

    /** 字典项显示文本, 直接用于下拉选项与翻译结果, 例如「已启用」 */
    val itemLabel: String

    /** 字典项存储值, 业务表保存的就是该值, 例如 enabled; 同字典内唯一, 是值翻译的回查键 */
    val itemValue: String

    /** 表单默认选中项; 同字典下只应有一个默认项, 由 Service 写入时校验 */
    @Default("false")
    val isDefault: Boolean

    /** 前端标签颜色, 取值为 EnumColor 调色板令牌或 #RRGGBB 色值, 与业务枚举 color 共用约定; 用于状态徽标渲染, 只影响展示不参与业务判断 */
    val color: String?

    /** 前端自定义样式类名, 供个别场景覆盖默认样式; 无样式需求时为空 */
    val cssClass: String?

    /** 同字典内的展示顺序, 升序排列; 默认 0 表示按创建顺序兜底 */
    @Default("0")
    val sortOrder: Int

    /** 字典项启停状态; disabled 后不再出现在下拉与翻译结果中, 但已保存的历史值保持不变 */
    @Default("enabled")
    val status: String
}

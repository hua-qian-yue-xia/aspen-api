package com.zax.aspen.admin.api.vo.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import java.time.LocalDateTime

/**
 * 字典项的管理查询视图
 *
 * 管理端列表与写操作返回的模型, 覆盖 sys_dict_item 全部业务列与关键审计列;
 * 扁平结构携带 parentId, 树形字典由前端按父子关系组树
 */
data class SysDictItemVO(
    /** 数据库主键 */
    val dictItemId: Long,
    /** 所属字典主键 id */
    val dictId: Long,
    /** 父字典项主键 id, 扁平字典为空 */
    val parentId: Long?,
    /** 字典项显示文本, 直接用于下拉选项与翻译结果 */
    val itemLabel: String,
    /** 字典项存储值, 业务表保存的就是该值, 创建后不可修改 */
    val itemValue: String,
    /** 表单默认选中项; 同字典下最多一个默认项 */
    val isDefault: Boolean,
    /** 前端标签颜色, EnumColor 调色板令牌或 #RRGGBB 色值 */
    val color: String?,
    /** 前端自定义样式类名 */
    val cssClass: String?,
    /** 同字典内的展示顺序, 升序排列 */
    val sortOrder: Int,
    /** 启停状态; disabled 后不再出现在下拉与翻译结果中 */
    val status: EnabledStatus,
    /** 乐观锁版本 */
    val version: Int,
    /** 创建时间 */
    val createdAt: LocalDateTime,
    /** 最近更新时间 */
    val updatedAt: LocalDateTime,
)

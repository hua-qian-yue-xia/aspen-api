package com.zax.aspen.admin.biz.entity.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table

/**
 * 保存全局枚举型业务字典的定义
 *
 * 典型场景: 下拉选项渲染、列表值翻译和前端标签着色都以本表与字典项表为数据源;
 * 业务代码通过 dictCode 定位字典, 不使用数据库主键; 字典是平台引用数据, 全体租户共用,
 * 将来需要按租户定制展示时以独立覆盖表扩展, 不回填租户列
 */
@Entity
@Table(name = "sys_dict")
interface SysDictEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val dictId: Long

    /** 字典编码, 业务代码以其定位字典, 例如 user_status、gender; 全局唯一, 创建后不可修改 */
    val dictCode: String

    /** 字典显示名, 用于管理界面展示与搜索, 例如「用户状态」 */
    val dictName: String

    /** 字典分组, 管理界面按域筛选字典, 例如 common/upm; @GenDict 播种与手工建字典时提供 */
    @Default("common")
    val dictGroup: String

    /** 标记系统内置字典; 内置字典禁止业务删除, 只允许调整显示属性, 防止系统依赖的枚举翻译丢失 */
    @Default("false")
    val isBuiltIn: Boolean

    /** 字典启停状态; disabled 后该字典不参与下拉渲染与值翻译, 已保存的历史值不受影响 */
    @Default("ENABLED")
    val status: EnabledStatus
}

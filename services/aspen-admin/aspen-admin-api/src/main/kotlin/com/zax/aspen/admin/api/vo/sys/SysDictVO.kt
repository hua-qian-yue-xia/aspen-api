package com.zax.aspen.admin.api.vo.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import java.time.LocalDateTime

/**
 * 字典的管理查询视图
 *
 * 管理端列表与详情的返回模型, 覆盖 sys_dict 全部业务列与关键审计列;
 * 字典项经 SysDictItemApi 单独查询, 列表视图不内嵌集合
 */
data class SysDictVO(
    /** 数据库主键 */
    val dictId: Long,
    /** 字典编码, 业务代码以其定位字典, 创建后不可修改 */
    val dictCode: String,
    /** 字典显示名, 用于管理界面展示与搜索 */
    val dictName: String,
    /** 字典分组, 管理界面按域筛选字典 */
    val dictGroup: String,
    /** 是否系统内置字典; 内置字典禁止删除与停用, 字典项禁止运营增删 */
    val isBuiltIn: Boolean,
    /** 启停状态; disabled 后该字典不参与下拉渲染与值翻译 */
    val status: EnabledStatus,
    /** 乐观锁版本 */
    val version: Int,
    /** 创建时间 */
    val createdAt: LocalDateTime,
    /** 最近更新时间 */
    val updatedAt: LocalDateTime,
)

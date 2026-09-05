package com.zax.aspen.common.core.enums.common

import com.zax.aspen.common.core.enums.AspenEnum

/**
 * 排序方向
 *
 * 取值对齐 SQL 标准的 ASC/DESC 关键字, 用于分页查询协议中声明排序字段的方向;
 * 与 PageQuery 配套使用, Repository 将其映射为 Jimmer 的排序表达式, 禁止把前端传入的
 * 排序方向直接拼接进 SQL
 */
enum class SortDirection(
    override val code: String,
    override val description: String,
) : AspenEnum {
    /** 升序, 对应 SQL ASC; 未指定方向时的默认值 */
    ASC("asc", "升序"),

    /** 降序, 对应 SQL DESC */
    DESC("desc", "降序"),
    ;

    override val color: String? = null
}

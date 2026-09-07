package com.zax.aspen.admin.api.vo.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
import java.time.LocalDateTime

/**
 * 网关路由的管理查询视图
 *
 * 管理端列表与详情的返回模型, 覆盖 sys_route 全部业务列与关键审计列;
 * predicates、filters 复用发布快照的结构类型, 保证管理面与分发面结构一致
 */
data class SysRouteVO(
    /** 数据库主键 */
    val routeId: Long,
    /** 路由编码, 即 Gateway route id, 创建后不可修改 */
    val routeCode: String,
    /** 路由显示名, 用于管理界面展示与搜索 */
    val routeName: String,
    /** 目标地址, lb:// 或 http(s):// */
    val uri: String,
    /** 路由匹配顺序, 数值小者先匹配 */
    val sortOrder: Int,
    /** 启停状态; disabled 的路由不进入发布快照 */
    val status: EnabledStatus,
    /** 断言列表, 至少一条 */
    val predicates: List<RouteDefinitionPart>,
    /** 过滤器列表, 可为空 */
    val filters: List<RouteDefinitionPart>,
    /** 路由级参数 */
    val metadata: Map<String, String>,
    /** 乐观锁版本 */
    val version: Int,
    /** 创建时间 */
    val createdAt: LocalDateTime,
    /** 最近更新时间 */
    val updatedAt: LocalDateTime,
)

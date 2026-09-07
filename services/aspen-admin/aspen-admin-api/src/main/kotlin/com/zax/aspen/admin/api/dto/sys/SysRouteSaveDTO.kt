package com.zax.aspen.admin.api.dto.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.gateway.contract.RouteDefinitionPart

/**
 * 网关路由的新增或修改入参
 *
 * 管理端写入模型; predicates、filters 复用 api/event 分发契约的部件类型,
 * 保证管理面入参与分发面结构一致; 结构合法性 (编码格式、uri 协议、断言存在性)
 * 由契约类型构造校验兜底, 唯一性与存在性由 Admin Service 校验; 修改时 routeCode 不可变更
 */
data class SysRouteSaveDTO(
    /** 路由编码; 新增时必填, 修改时必须与既有值一致 */
    val routeCode: String,
    /** 路由显示名 */
    val routeName: String,
    /** 目标地址, 只允许 lb://、http:// 或 https:// */
    val uri: String,
    /** 路由匹配顺序, 默认 0 */
    val sortOrder: Int = 0,
    /** 启停状态, 默认启用 */
    val status: EnabledStatus = EnabledStatus.ENABLED,
    /** 断言列表, 至少一条 */
    val predicates: List<RouteDefinitionPart>,
    /** 过滤器列表, 可为空 */
    val filters: List<RouteDefinitionPart> = emptyList(),
    /** 路由级参数, 可为空 */
    val metadata: Map<String, String> = emptyMap(),
)

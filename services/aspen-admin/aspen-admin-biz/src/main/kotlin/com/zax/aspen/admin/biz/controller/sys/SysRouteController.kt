package com.zax.aspen.admin.biz.controller.sys

import com.zax.aspen.admin.api.contract.sys.SysRouteApi
import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.api.vo.sys.SysRouteVO
import com.zax.aspen.admin.biz.service.sys.SysRouteService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController

/**
 * 网关路由管理端点
 *
 * 路径与映射继承 admin-api 的 SysRouteApi; 属于 internal 契约, 不经网关暴露,
 * v1 无鉴权, 依赖「默认关闭 + 网络不暴露」兜底, RBAC 就绪后转正式管理 API
 */
@RestController
@ConditionalOnProperty(prefix = "aspen.admin.route-api", name = ["enabled"], havingValue = "true")
class SysRouteController(
    private val sysRouteService: SysRouteService,
) : SysRouteApi {
    override fun listRoutes(): List<SysRouteVO> = sysRouteService.listRoutes()

    override fun createRoute(command: SysRouteSaveDTO): SysRouteVO = sysRouteService.createRoute(command)

    override fun updateRoute(routeId: Long, command: SysRouteSaveDTO): SysRouteVO =
        sysRouteService.updateRoute(routeId, command)

    override fun deleteRoute(routeId: Long) = sysRouteService.deleteRoute(routeId)
}

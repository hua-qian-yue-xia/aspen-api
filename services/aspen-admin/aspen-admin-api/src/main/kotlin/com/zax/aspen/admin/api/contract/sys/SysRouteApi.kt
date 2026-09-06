package com.zax.aspen.admin.api.contract.sys

import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.api.vo.sys.SysRouteVO
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 网关路由管理的内部契约
 *
 * 属于 internal 契约, 不经网关暴露; v1 无鉴权, 依赖「默认关闭 + 网络不暴露」兜底,
 * RBAC 就绪后转正式管理 API; 每个变更操作在事务提交后触发路由快照发布
 */
interface SysRouteApi {
    /**
     * 查询全部未删除路由, 按匹配顺序排列
     *
     * @return 路由视图列表, 按 sortOrder 升序, 无数据时返回空列表
     */
    @GetMapping(PATH)
    fun listRoutes(): List<SysRouteVO>

    /**
     * 新增路由; 路由编码重复时拒绝
     *
     * @param command 路由新增入参, routeCode 全局唯一
     * @return 已落库的新路由视图, 含生成的 id 与版本号
     */
    @PostMapping(PATH)
    fun createRoute(
        @RequestBody command: SysRouteSaveDTO,
    ): SysRouteVO

    /**
     * 修改路由; 路由编码不可变更, 乐观锁冲突时拒绝
     *
     * @param routeId 目标路由的主键 id
     * @param command 路由修改入参, routeCode 必须与既有值一致
     * @return 修改后的路由视图, 版本号随更新自增
     */
    @PutMapping("$PATH/{routeId}")
    fun updateRoute(
        @PathVariable("routeId") routeId: Long,
        @RequestBody command: SysRouteSaveDTO,
    ): SysRouteVO

    /**
     * 逻辑删除路由并从发布快照移除
     *
     * @param routeId 目标路由的主键 id
     */
    @DeleteMapping("$PATH/{routeId}")
    fun deleteRoute(
        @PathVariable("routeId") routeId: Long,
    )

    companion object {
        /** 管理端点路径前缀 */
        const val PATH = "/internal/sys/route"
    }
}

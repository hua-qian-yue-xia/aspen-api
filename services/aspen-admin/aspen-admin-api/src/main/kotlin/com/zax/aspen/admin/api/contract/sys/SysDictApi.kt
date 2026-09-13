package com.zax.aspen.admin.api.contract.sys

import com.zax.aspen.admin.api.dto.sys.SysDictPageQuery
import com.zax.aspen.admin.api.dto.sys.SysDictSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictUpdateDTO
import com.zax.aspen.admin.api.vo.sys.SysDictVO
import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.common.route.DeleteRoute
import com.zax.aspen.common.route.GetRoute
import com.zax.aspen.common.route.OperationTag
import com.zax.aspen.common.route.PostRoute
import com.zax.aspen.common.route.PutRoute
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody

/**
 * 字典管理的对外契约
 *
 * 由 admin-biz 的 controller/admin/sys 实现, 经 aspen-common-web 自动携带 /admin-api
 * 受众前缀, 网关按 /admin-api 前缀路由到本服务; 内置字典 (枚举播种生成) 受保护:
 * 禁止删除与停用, 字典项增删同样禁止 (见 SysDictItemApi); 端点声明使用路由套件注解
 * (common-route), 同时获得文档摘要、默认限流与操作日志分类
 */
interface SysDictApi {
    /**
     * 分页查询字典
     *
     * @param query 分页与过滤条件, 条件全部可空, 编码/名称为模糊匹配
     * @return 字典视图分页结果, 按 dictCode 升序
     */
    @GetRoute("$PATH/page", summary = "分页查询字典")
    fun pageDicts(
        @Valid query: SysDictPageQuery,
    ): PageResult<SysDictVO>

    /**
     * 查询字典详情
     *
     * @param dictId 目标字典的主键 id
     * @return 字典视图, 不存在或已逻辑删除时拒绝
     */
    @GetRoute("$PATH/{dictId}", summary = "查询字典详情")
    fun getDict(
        @PathVariable("dictId") dictId: Long,
    ): SysDictVO

    /**
     * 新增字典; 编码重复或已被逻辑删除行占用时拒绝
     *
     * @param command 字典新增入参, dictCode 全局唯一且永久占用
     * @return 已落库的字典视图, 手工创建的字典固定为非内置
     */
    @PostRoute(PATH, summary = "新增字典", log = OperationTag.INSERT)
    fun createDict(
        @RequestBody @Valid command: SysDictSaveDTO,
    ): SysDictVO

    /**
     * 修改字典显示属性; 编码不可变更, 内置字典同样只允许调整显示属性
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @param command 字典修改入参, 携带显示名与分组
     * @return 修改并重查全行后的字典视图
     */
    @PutRoute("$PATH/{dictId}", summary = "修改字典显示属性", log = OperationTag.UPDATE)
    fun updateDict(
        @PathVariable("dictId") dictId: Long,
        @RequestBody @Valid command: SysDictUpdateDTO,
    ): SysDictVO

    /**
     * 修改字典启停状态; 内置字典不允许停用, 防止系统依赖的枚举翻译丢失
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @param command 目标启停状态
     * @return 状态变更并重查全行后的字典视图
     */
    @PutRoute("$PATH/{dictId}/status", summary = "修改字典启停状态", log = OperationTag.UPDATE)
    fun updateDictStatus(
        @PathVariable("dictId") dictId: Long,
        @RequestBody @Valid command: SysDictStatusUpdateDTO,
    ): SysDictVO

    /**
     * 删除字典及其全部字典项; 内置字典禁止删除
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     */
    @DeleteRoute("$PATH/{dictId}", summary = "删除字典", log = OperationTag.DELETE)
    fun deleteDict(
        @PathVariable("dictId") dictId: Long,
    )

    companion object {
        /** 管理端点路径前缀, 受众前缀 /admin-api 由 Controller 包位置决定, 此处只写相对路径 */
        const val PATH = "/sys/dict"
    }
}

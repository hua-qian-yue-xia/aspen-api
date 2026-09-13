package com.zax.aspen.admin.api.contract.sys

import com.zax.aspen.admin.api.dto.sys.SysDictItemSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemUpdateDTO
import com.zax.aspen.admin.api.vo.sys.SysDictItemVO
import com.zax.aspen.common.route.DeleteRoute
import com.zax.aspen.common.route.GetRoute
import com.zax.aspen.common.route.OperationTag
import com.zax.aspen.common.route.PostRoute
import com.zax.aspen.common.route.PutRoute
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

/**
 * 字典项管理的对外契约
 *
 * 由 admin-biz 的 controller/admin/sys 实现, 经 aspen-common-web 自动携带 /admin-api
 * 受众前缀; 内置字典的字典项由枚举播种维护, 禁止运营增删, 只允许调整展示属性;
 * 字典项值与父项创建后不可修改 (改值需新建项并废弃旧项, 树形结构的子树移动属二期),
 * 同字典内值唯一, 默认项唯一由 Service 校验
 */
interface SysDictItemApi {
    /**
     * 查询指定字典下的全部字典项
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @return 字典项视图列表, 按 sortOrder 升序, 扁平结构携带 parentId 供前端组树
     */
    @GetRoute("$PATH/list", summary = "查询字典项列表")
    fun listItems(
        @RequestParam("dictId") dictId: Long,
    ): List<SysDictItemVO>

    /**
     * 新增字典项; 所属字典为内置字典时拒绝, 同字典内值重复时拒绝
     *
     * @param command 字典项新增入参, parentId 必须属于同一字典
     * @return 已落库的字典项视图
     */
    @PostRoute(PATH, summary = "新增字典项", log = OperationTag.INSERT)
    fun createItem(
        @RequestBody @Valid command: SysDictItemSaveDTO,
    ): SysDictItemVO

    /**
     * 修改字典项展示属性; 值、所属字典与父项不可变更, 内置字典项同样可调整
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     * @param command 字典项修改入参, isDefault 置位时清除同字典其他默认项
     * @return 修改并重查全行后的字典项视图
     */
    @PutRoute("$PATH/{itemId}", summary = "修改字典项展示属性", log = OperationTag.UPDATE)
    fun updateItem(
        @PathVariable("itemId") itemId: Long,
        @RequestBody @Valid command: SysDictItemUpdateDTO,
    ): SysDictItemVO

    /**
     * 修改字典项启停状态; 停用后不再出现在下拉与翻译结果, 历史值不受影响
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     * @param command 目标启停状态
     * @return 状态变更并重查全行后的字典项视图
     */
    @PutRoute("$PATH/{itemId}/status", summary = "修改字典项启停状态", log = OperationTag.UPDATE)
    fun updateItemStatus(
        @PathVariable("itemId") itemId: Long,
        @RequestBody @Valid command: SysDictItemStatusUpdateDTO,
    ): SysDictItemVO

    /**
     * 删除字典项; 所属字典为内置字典或仍存在子项时拒绝
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     */
    @DeleteRoute("$PATH/{itemId}", summary = "删除字典项", log = OperationTag.DELETE)
    fun deleteItem(
        @PathVariable("itemId") itemId: Long,
    )

    companion object {
        /** 管理端点路径前缀, 受众前缀 /admin-api 由 Controller 包位置决定, 此处只写相对路径 */
        const val PATH = "/sys/dict/item"
    }
}

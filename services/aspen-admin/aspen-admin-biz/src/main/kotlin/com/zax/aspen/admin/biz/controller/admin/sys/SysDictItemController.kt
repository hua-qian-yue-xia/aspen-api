package com.zax.aspen.admin.biz.controller.admin.sys

import com.zax.aspen.admin.api.contract.sys.SysDictItemApi
import com.zax.aspen.admin.api.dto.sys.SysDictItemSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemUpdateDTO
import com.zax.aspen.admin.api.vo.sys.SysDictItemVO
import com.zax.aspen.admin.biz.service.sys.SysDictService
import org.springframework.web.bind.annotation.RestController

/**
 * 字典项管理端点
 *
 * 路径与路由套件声明继承 admin-api 的 SysDictItemApi; 位于 controller/admin 受众包,
 * 经 aspen-common-web 自动携带 /admin-api 前缀经网关对外暴露; 内置字典项的
 * 保护规则由 SysDictService 强制
 */
@RestController
class SysDictItemController(
    private val sysDictService: SysDictService,
) : SysDictItemApi {
    override fun listItems(dictId: Long): List<SysDictItemVO> = sysDictService.listItems(dictId)

    override fun createItem(command: SysDictItemSaveDTO): SysDictItemVO = sysDictService.createItem(command)

    override fun updateItem(itemId: Long, command: SysDictItemUpdateDTO): SysDictItemVO =
        sysDictService.updateItem(itemId, command)

    override fun updateItemStatus(itemId: Long, command: SysDictItemStatusUpdateDTO): SysDictItemVO =
        sysDictService.updateItemStatus(itemId, command)

    override fun deleteItem(itemId: Long) = sysDictService.deleteItem(itemId)
}

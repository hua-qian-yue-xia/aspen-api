package com.zax.aspen.admin.biz.controller.admin.sys

import com.zax.aspen.admin.api.contract.sys.SysDictApi
import com.zax.aspen.admin.api.dto.sys.SysDictPageQuery
import com.zax.aspen.admin.api.dto.sys.SysDictSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictUpdateDTO
import com.zax.aspen.admin.api.vo.sys.SysDictVO
import com.zax.aspen.admin.biz.service.sys.SysDictService
import com.zax.aspen.common.core.page.PageResult
import org.springframework.web.bind.annotation.RestController

/**
 * 字典管理端点
 *
 * 路径与路由套件声明继承 admin-api 的 SysDictApi; 位于 controller/admin 受众包,
 * 经 aspen-common-web 自动携带 /admin-api 前缀经网关对外暴露
 */
@RestController
class SysDictController(
    private val sysDictService: SysDictService,
) : SysDictApi {
    override fun pageDicts(query: SysDictPageQuery): PageResult<SysDictVO> = sysDictService.pageDicts(query)

    override fun getDict(dictId: Long): SysDictVO = sysDictService.getDict(dictId)

    override fun createDict(command: SysDictSaveDTO): SysDictVO = sysDictService.createDict(command)

    override fun updateDict(dictId: Long, command: SysDictUpdateDTO): SysDictVO =
        sysDictService.updateDict(dictId, command)

    override fun updateDictStatus(dictId: Long, command: SysDictStatusUpdateDTO): SysDictVO =
        sysDictService.updateDictStatus(dictId, command)

    override fun deleteDict(dictId: Long) = sysDictService.deleteDict(dictId)
}

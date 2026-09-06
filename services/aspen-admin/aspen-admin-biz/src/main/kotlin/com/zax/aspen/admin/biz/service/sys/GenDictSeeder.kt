package com.zax.aspen.admin.biz.service.sys

import com.zax.aspen.admin.biz.repository.sys.SysDictRepository
import com.zax.aspen.common.core.gen.GenDictDescriptor
import com.zax.aspen.common.gen.autoconfigure.AspenGenProperties
import com.zax.aspen.common.gen.boot.GenDictSink
import com.zax.aspen.common.gen.scan.GenDictCatalog
import jakarta.annotation.Resource
import org.springframework.stereotype.Service

/**
 * 把 @GenDict 扫描出的枚举字典目录幂等播种进 sys_dict 与 sys_dict_item
 *
 * SYS 组常驻业务服务, 作为 Admin 的 GenDictSink 实现承接启动投递, 远端上报经
 * controller 复用同一入口; 默认 create-missing 只补缺, 保护运营对展示属性的定制;
 * resync 强制回写展示属性, 不触碰启停、默认项、样式类与父级; 枚举仍是唯一权威取值来源
 */
@Service
class GenDictSeeder : GenDictSink {
    @Resource
    private lateinit var sysDictRepository: SysDictRepository

    @Resource
    private lateinit var aspenGenProperties: AspenGenProperties

    override fun deliver(catalog: GenDictCatalog) {
        seed(catalog.descriptors, aspenGenProperties.dict.mode)
    }

    /**
     * 按指定模式幂等播种整个目录; 播种语句各自提交, 中断后可重跑补齐
     *
     * @param catalog 待播种的字典描述列表, 每项对应一个枚举字典
     * @param mode 播种模式, create-missing 只补缺或 resync 强制回写展示属性
     */
    fun seed(catalog: List<GenDictDescriptor>, mode: AspenGenProperties.Dict.Mode) {
        catalog.forEach { descriptor -> seedDescriptor(descriptor, mode) }
    }

    /**
     * 播种单个字典: 先确保字典行存在, 再逐项补缺或回写
     *
     * @param descriptor 单个枚举字典描述, 含字典行字段与全部字典项
     * @param mode 播种模式, 决定已有字典与字典项是补缺还是强制回写
     */
    private fun seedDescriptor(descriptor: GenDictDescriptor, mode: AspenGenProperties.Dict.Mode) {
        val existing = sysDictRepository.findDictByCode(descriptor.dictCode)
        val dictId = when {
            existing == null -> sysDictRepository.insertDict(descriptor).dictId
            mode == AspenGenProperties.Dict.Mode.RESYNC -> {
                sysDictRepository.resyncDict(existing, descriptor)
                existing.dictId
            }
            else -> existing.dictId
        }
        val existingItems = sysDictRepository.findItemsByDictId(dictId).associateBy { it.itemValue }
        descriptor.items.forEach { item ->
            val existingItem = existingItems[item.itemValue]
            when {
                existingItem == null -> sysDictRepository.insertItem(dictId, item)
                mode == AspenGenProperties.Dict.Mode.RESYNC -> sysDictRepository.resyncItem(existingItem, item)
                // create-missing 不改已有项, 保留运营对 label/color/顺序的覆盖
            }
        }
    }
}

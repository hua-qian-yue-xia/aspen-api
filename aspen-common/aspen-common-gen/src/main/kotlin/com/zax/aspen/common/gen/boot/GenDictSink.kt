package com.zax.aspen.common.gen.boot

import com.zax.aspen.common.gen.scan.GenDictCatalog

/**
 * 枚举字典目录的投递 SPI
 *
 * 使用方实现本接口决定扫描出的目录去向: Admin 的实现直接落库到 sys_dict 与
 * sys_dict_item, 其他服务可实现远程上报到 Admin 的 internal 契约;
 * common-gen 的启动 Runner 在播种开启时把目录投递给容器内的全部实现
 */
fun interface GenDictSink {
    /** 接收本服务扫描出的字典目录并投递到目的地 */
    fun deliver(catalog: GenDictCatalog)
}

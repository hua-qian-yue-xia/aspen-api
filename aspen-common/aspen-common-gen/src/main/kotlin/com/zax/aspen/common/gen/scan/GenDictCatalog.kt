package com.zax.aspen.common.gen.scan

import com.zax.aspen.common.core.gen.GenDictDescriptor

/**
 * 枚举字典目录, 承载一次扫描的全部产物
 *
 * 独立包装类型避免把 List 直接注册为 Bean 后被 Spring 的集合注入语义拆散
 */
class GenDictCatalog(
    /** 按字典编码升序排列的全部字典描述 */
    val descriptors: List<GenDictDescriptor>,
)

package com.zax.aspen.common.gen.scan

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.gen.GenDict
import com.zax.aspen.common.core.gen.GenDictDescriptor
import com.zax.aspen.common.core.gen.GenDictItemDescriptor
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter

/** 扫描 @GenDict 枚举并产出字典目录, 供 Admin 播种或跨服务上报 */
class GenDictScanner(
    /** 允许扫描的包前缀 */
    private val basePackages: List<String>,
) {
    /**
     * 扫描标注 @GenDict 的 AspenEnum 枚举, 按编码升序返回; 存在重复编码时拒绝
     *
     * @return 按字典编码升序排列且编码唯一的字典目录
     */
    fun scan(): GenDictCatalog {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AssignableTypeFilter(AspenEnum::class.java))
        val descriptors = basePackages.flatMap { basePackage ->
            scanner.findCandidateComponents(basePackage)
                .mapNotNull { candidate -> candidate.beanClassName }
                .mapNotNull { className -> runCatching { Class.forName(className) }.getOrNull() }
                .filter { enumType -> enumType.isEnum && AspenEnum::class.java.isAssignableFrom(enumType) }
                .mapNotNull { enumType -> describe(enumType) }
        }
        val duplicatedCodes = descriptors.groupBy { it.dictCode }.filterValues { it.size > 1 }.keys
        require(duplicatedCodes.isEmpty()) { "存在重复的字典编码: $duplicatedCodes" }
        return GenDictCatalog(descriptors.sortedBy { it.dictCode })
    }

    /**
     * 把单个枚举转换为字典描述; 未标注 @GenDict 时返回 null 表示不参与播种
     *
     * @param enumType 实现 AspenEnum 的枚举类型
     * @return 字典描述, 枚举未标注 @GenDict 时返回 `null`
     */
    private fun describe(enumType: Class<*>): GenDictDescriptor? {
        val genDict = enumType.getAnnotation(GenDict::class.java) ?: return null
        val items = enumType.enumConstants
            .map { constant ->
                GenDictItemDescriptor(
                    itemValue = (constant as AspenEnum).code,
                    itemLabel = (constant as AspenEnum).description,
                    color = (constant as AspenEnum).color,
                    sortOrder = (constant as Enum<*>).ordinal,
                )
            }
        return GenDictDescriptor(
            dictCode = genDict.code,
            dictName = genDict.name,
            dictGroup = genDict.group,
            items = items,
        )
    }
}

package com.zax.aspen.common.core.gen

/**
 * 枚举字典的扫描产物, 作为跨服务上报与 Admin 播种的传输模型
 *
 * 由 common-gen 扫描 @GenDict 枚举生成; 框架无关的纯数据类型, 可直接进入 HTTP 契约,
 * 构造时校验编码、分组格式与项值唯一, 使非法目录在任何消费方之前即失败
 */
data class GenDictDescriptor(
    /** 字典编码, 对应 sys_dict.dict_code; 全局唯一, 小写下划线格式 */
    val dictCode: String,
    /** 字典显示名, 对应 sys_dict.dict_name */
    val dictName: String,
    /** 字典分组, 对应 sys_dict.dict_group; 小写下划线格式 */
    val dictGroup: String,
    /** 字典项, 按枚举声明顺序排列, 至少一项 */
    val items: List<GenDictItemDescriptor>,
) {
    init {
        require(dictCode.matches(CODE_PATTERN)) { "字典编码必须为小写下划线格式: $dictCode" }
        require(dictGroup.matches(CODE_PATTERN)) { "字典分组必须为小写下划线格式: $dictGroup" }
        require(dictName.isNotBlank()) { "字典显示名不得为空: $dictCode" }
        require(items.isNotEmpty()) { "字典至少需要一个字典项: $dictCode" }
        val duplicatedValues = items.groupBy { it.itemValue }.filterValues { it.size > 1 }.keys
        require(duplicatedValues.isEmpty()) { "字典存在重复存储值: $dictCode -> $duplicatedValues" }
    }

    private companion object {
        /** 校验编码与分组的小写下划线格式 */
        val CODE_PATTERN = Regex("[a-z][a-z0-9_]*")
    }
}

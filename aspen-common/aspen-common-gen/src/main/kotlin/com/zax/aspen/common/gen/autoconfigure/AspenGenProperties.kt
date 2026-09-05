package com.zax.aspen.common.gen.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/** 绑定 Aspen 生成工具配置 */
@ConfigurationProperties("aspen.gen")
class AspenGenProperties {
    /** 枚举字典播种配置 */
    val dict: Dict = Dict()

    /** 控制枚举字典扫描、上报与播种 */
    class Dict {
        /** 是否启用枚举字典播种; 默认关闭, 生产环境禁止开启 */
        var enabled: Boolean = false

        /** 播种模式: create-missing 只补缺, resync 强制回写展示属性 */
        var mode: Mode = Mode.CREATE_MISSING

        /** 扫描 @GenDict 枚举的包范围 */
        var basePackages: List<String> = listOf("com.zax.aspen")

        /** 播种模式 */
        enum class Mode {
            /** 只新增缺失的字典与字典项, 绝不修改已有行 */
            CREATE_MISSING,

            /** 强制回写展示属性, 但不触碰启停、默认项、样式类与父级 */
            RESYNC,
        }
    }
}

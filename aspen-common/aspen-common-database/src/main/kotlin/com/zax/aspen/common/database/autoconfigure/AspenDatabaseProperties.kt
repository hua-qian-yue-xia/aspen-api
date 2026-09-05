package com.zax.aspen.common.database.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/** 绑定 Aspen 数据库公共配置 */
@ConfigurationProperties("aspen.database")
class AspenDatabaseProperties {
    /** 审计字段配置 */
    val audit: Audit = Audit()

    /** 分页限制配置 */
    val pagination: Pagination = Pagination()

    /** 批处理限制配置 */
    val batch: Batch = Batch()

    /** 枚举映射配置 */
    val enums: Enums = Enums()

    /** 控制公共审计拦截器 */
    class Audit {
        /** 是否启用公共审计时间写入 */
        var enabled: Boolean = true
    }

    /** 定义数据库分页默认值和上限 */
    class Pagination {
        /** 默认单页数量 */
        var defaultSize: Int = 20

        /** 允许的最大单页数量 */
        var maxSize: Int = 200
    }

    /** 定义数据库批处理默认值和上限 */
    class Batch {
        /** 默认批处理数量 */
        var defaultSize: Int = 100

        /** 允许的最大批处理数量 */
        var maxSize: Int = 1_000
    }

    /** 定义 AspenEnum 枚举扫描范围 */
    class Enums {
        /** 扫描实现 AspenEnum 的枚举所在包, 扫描结果注册为 Jimmer 标量转换器 */
        var basePackages: List<String> = listOf("com.zax.aspen")
    }
}

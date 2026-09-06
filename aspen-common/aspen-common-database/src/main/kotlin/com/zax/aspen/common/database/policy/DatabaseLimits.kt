package com.zax.aspen.common.database.policy

/** 定义 Repository 和 Service 共同遵守的数据库操作限制 */
data class DatabaseLimits(
    /** 默认单页数量 */
    val defaultPageSize: Int,
    /** 允许的最大单页数量 */
    val maxPageSize: Int,
    /** 默认批处理数量 */
    val defaultBatchSize: Int,
    /** 允许的最大批处理数量 */
    val maxBatchSize: Int,
) {
    // 配置错误必须在应用启动阶段暴露
    init {
        require(defaultPageSize >= 1) { "aspen.database.pagination.default-size 必须为正数" }
        require(maxPageSize >= defaultPageSize) {
            "aspen.database.pagination.max-size 不能小于 default-size"
        }
        require(defaultBatchSize >= 1) { "aspen.database.batch.default-size 必须为正数" }
        require(maxBatchSize >= defaultBatchSize) {
            "aspen.database.batch.max-size 不能小于 default-size"
        }
    }

    /**
     * 校验业务请求的单页数量并在合法时原样返回
     *
     * @param pageSize 业务请求的单页数量
     * @return 校验通过的单页数量, 即入参原值
     * @throws IllegalArgumentException pageSize 超出 1 到 maxPageSize 范围时拒绝
     */
    fun requirePageSize(pageSize: Int): Int {
        require(pageSize in 1..maxPageSize) { "pageSize 必须在 1 到 $maxPageSize 之间" }
        return pageSize
    }

    /**
     * 校验业务请求的批处理数量并在合法时原样返回
     *
     * @param batchSize 业务请求的单批处理数量
     * @return 校验通过的批处理数量, 即入参原值
     * @throws IllegalArgumentException batchSize 超出 1 到 maxBatchSize 范围时拒绝
     */
    fun requireBatchSize(batchSize: Int): Int {
        require(batchSize in 1..maxBatchSize) { "batchSize 必须在 1 到 $maxBatchSize 之间" }
        return batchSize
    }
}

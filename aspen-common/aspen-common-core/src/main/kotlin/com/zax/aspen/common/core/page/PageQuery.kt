package com.zax.aspen.common.core.page

/**
 * 定义不依赖分页框架且从 1 开始计数的分页请求
 */
data class PageQuery(
    /** 从 1 开始的目标页码 */
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    /** 单页请求的数据条数 */
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    // 在构造阶段拒绝无法形成有效数据库分页的参数
    init {
        require(pageNumber >= 1) { "pageNumber 不能小于 1" }
        require(pageSize >= 1) { "pageSize 不能小于 1" }
    }

    /** 根据页码和单页数量安全计算数据库偏移量 */
    val offset: Long
        get() = Math.multiplyExact((pageNumber - 1).toLong(), pageSize.toLong())

    /**
     * 保存分页请求的公共默认值
     */
    companion object {
        /** 默认从第 1 页开始 */
        const val DEFAULT_PAGE_NUMBER: Int = 1

        /** 默认每页返回 20 条数据 */
        const val DEFAULT_PAGE_SIZE: Int = 20
    }
}

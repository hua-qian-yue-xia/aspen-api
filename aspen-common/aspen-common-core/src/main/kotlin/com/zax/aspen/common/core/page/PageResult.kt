package com.zax.aspen.common.core.page

/** 定义适合 API 契约使用且不依赖分页框架的分页结果 */
data class PageResult<T>(
    /** 当前页的数据列表 */
    val items: List<T>,
    /** 满足查询条件的数据总数 */
    val totalElements: Long,
    /** 从 1 开始的当前页码 */
    val pageNumber: Int,
    /** 当前页使用的单页数量 */
    val pageSize: Int,
    /** 根据总数和单页数量计算得到的总页数 */
    val totalPages: Long = calculateTotalPages(totalElements, pageSize),
) {
    // 防止调用方构造总数和总页数互相矛盾的协议对象
    init {
        require(totalElements >= 0) { "totalElements 不能为负数" }
        require(pageNumber >= 1) { "pageNumber 不能小于 1" }
        require(pageSize >= 1) { "pageSize 不能小于 1" }
        require(totalPages >= 0) { "totalPages 不能为负数" }
        require(totalPages == calculateTotalPages(totalElements, pageSize)) {
            "totalPages 与 totalElements 和 pageSize 的计算结果不一致"
        }
    }

    /** 提供分页结果的公共构造方法 */
    companion object {
        /** 根据分页请求创建空结果 */
        fun <T> empty(query: PageQuery): PageResult<T> = PageResult(
            items = emptyList(),
            totalElements = 0,
            pageNumber = query.pageNumber,
            pageSize = query.pageSize,
        )

        /** 使用避免加法溢出的方式计算总页数 */
        private fun calculateTotalPages(totalElements: Long, pageSize: Int): Long {
            require(pageSize >= 1) { "pageSize 不能小于 1" }
            if (totalElements == 0L) {
                return 0
            }
            return 1 + (totalElements - 1) / pageSize
        }
    }
}

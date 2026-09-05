package com.zax.aspen.common.core.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证分页协议的边界和计算规则 */
class PageTypesTest {
    /** 验证页码偏移和总页数使用无溢出的计算方式 */
    @Test
    fun `calculates a safe offset and total page count`() {
        val query = PageQuery(pageNumber = 3, pageSize = 20)
        val result = PageResult(
            items = listOf("first", "second"),
            totalElements = 41,
            pageNumber = query.pageNumber,
            pageSize = query.pageSize,
        )

        assertEquals(40, query.offset)
        assertEquals(3, result.totalPages)
    }

    /** 验证非法分页边界和矛盾的总页数会被拒绝 */
    @Test
    fun `rejects invalid page boundaries`() {
        assertFailsWith<IllegalArgumentException> { PageQuery(pageNumber = 0) }
        assertFailsWith<IllegalArgumentException> { PageQuery(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> {
            PageResult<String>(emptyList(), totalElements = -1, pageNumber = 1, pageSize = 20)
        }
        assertFailsWith<IllegalArgumentException> {
            PageResult<String>(emptyList(), totalElements = 21, pageNumber = 1, pageSize = 20, totalPages = 1)
        }
    }

    /** 验证空结果不会产生除零或多余页数 */
    @Test
    fun `creates an empty page without division edge cases`() {
        val result = PageResult.empty<String>(PageQuery())

        assertEquals(0, result.totalElements)
        assertEquals(0, result.totalPages)
        assertEquals(emptyList(), result.items)
    }
}

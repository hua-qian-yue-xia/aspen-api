package com.zax.aspen.common.database.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证数据库分页和批处理限制的配置边界 */
class DatabaseLimitsTest {
    /** 验证请求大小只能处于配置允许的范围内 */
    @Test
    fun `validates configured page and batch sizes`() {
        val limits = DatabaseLimits(20, 200, 100, 1_000)

        assertEquals(200, limits.requirePageSize(200))
        assertEquals(1_000, limits.requireBatchSize(1_000))
        assertFailsWith<IllegalArgumentException> { limits.requirePageSize(201) }
        assertFailsWith<IllegalArgumentException> { limits.requireBatchSize(1_001) }
    }

    /** 验证互相矛盾或非正数的限制配置会立即失败 */
    @Test
    fun `rejects invalid configuration`() {
        assertFailsWith<IllegalArgumentException> { DatabaseLimits(0, 200, 100, 1_000) }
        assertFailsWith<IllegalArgumentException> { DatabaseLimits(20, 10, 100, 1_000) }
        assertFailsWith<IllegalArgumentException> { DatabaseLimits(20, 200, 0, 1_000) }
        assertFailsWith<IllegalArgumentException> { DatabaseLimits(20, 200, 100, 99) }
    }
}

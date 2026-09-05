package com.zax.aspen.common.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证不依赖框架的文本校验规则 */
class ValidationTest {
    /** 验证长度范围内的可打印文本可以通过校验 */
    @Test
    fun `accepts bounded printable text`() {
        assertEquals("upm-user", Validation.requireSafeText("upm-user", "cacheName", 64))
    }

    /** 验证空白, 超长和控制字符输入会被拒绝 */
    @Test
    fun `rejects blank oversized and control character values`() {
        assertFailsWith<IllegalArgumentException> { Validation.requireSafeText(" ", "name", 10) }
        assertFailsWith<IllegalArgumentException> { Validation.requireSafeText("toolong", "name", 3) }
        assertFailsWith<IllegalArgumentException> { Validation.requireSafeText("bad\nvalue", "name", 20) }
    }
}

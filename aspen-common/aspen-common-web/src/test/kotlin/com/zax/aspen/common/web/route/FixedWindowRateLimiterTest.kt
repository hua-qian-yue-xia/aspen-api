package com.zax.aspen.common.web.route

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 进程内固定窗口限流器的单元验证
 *
 * 以可控时钟驱动窗口翻转, 验证配额计数、窗口重置、key 隔离与非正配置的放行语义
 */
class FixedWindowRateLimiterTest {
    /** 验证窗口内按配额放行, 超限拒绝 */
    @Test
    fun `allows up to limit within window and rejects beyond`() {
        val limiter = FixedWindowRateLimiter(SteppingClock(0))

        repeat(3) { assertTrue(limiter.tryAcquire("k", limit = 3, windowSeconds = 60), "配额内第 ${it + 1} 次应放行") }
        assertFalse(limiter.tryAcquire("k", limit = 3, windowSeconds = 60), "第 4 次应拒绝")
    }

    /** 验证跨过窗口长度后计数整体重置 */
    @Test
    fun `resets counter after window elapses`() {
        val clock = SteppingClock(0)
        val limiter = FixedWindowRateLimiter(clock)

        assertTrue(limiter.tryAcquire("k", limit = 1, windowSeconds = 60))
        assertFalse(limiter.tryAcquire("k", limit = 1, windowSeconds = 60))

        clock.current = Instant.ofEpochSecond(60)
        assertTrue(limiter.tryAcquire("k", limit = 1, windowSeconds = 60), "新窗口应重新计数")
        assertFalse(limiter.tryAcquire("k", limit = 1, windowSeconds = 60))
    }

    /** 验证不同 key 的窗口桶相互独立 */
    @Test
    fun `isolates counters per key`() {
        val limiter = FixedWindowRateLimiter(SteppingClock(0))

        assertTrue(limiter.tryAcquire("a", limit = 1, windowSeconds = 60))
        assertFalse(limiter.tryAcquire("a", limit = 1, windowSeconds = 60))
        assertTrue(limiter.tryAcquire("b", limit = 1, windowSeconds = 60), "key b 不受 key a 配额影响")
    }

    /** 验证非正 limit 或窗口长度视为不限流 */
    @Test
    fun `non positive config means unlimited`() {
        val limiter = FixedWindowRateLimiter(SteppingClock(0))

        repeat(5) {
            assertTrue(limiter.tryAcquire("k", limit = 0, windowSeconds = 60), "limit=0 应视为不限流")
            assertTrue(limiter.tryAcquire("k", limit = 1, windowSeconds = 0), "windowSeconds=0 应视为不限流")
        }
    }

    /** 验证窗口起点取首次命中时刻而非对齐墙钟 */
    @Test
    fun `window starts on first acquisition`() {
        val clock = SteppingClock(59)
        val limiter = FixedWindowRateLimiter(clock)

        assertTrue(limiter.tryAcquire("k", limit = 1, windowSeconds = 60))
        clock.current = Instant.ofEpochSecond(100)
        assertFalse(limiter.tryAcquire("k", limit = 1, windowSeconds = 60), "未满窗口 (59+60>100) 应继续计数拒绝")
        clock.current = Instant.ofEpochSecond(119)
        assertTrue(limiter.tryAcquire("k", limit = 1, windowSeconds = 60), "满窗口后应重置")
    }

    /** 可控时钟, 测试内直接推进时间验证窗口语义 */
    private class SteppingClock(
        startEpochSecond: Long,
    ) : Clock() {
        var current: Instant = Instant.ofEpochSecond(startEpochSecond)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}

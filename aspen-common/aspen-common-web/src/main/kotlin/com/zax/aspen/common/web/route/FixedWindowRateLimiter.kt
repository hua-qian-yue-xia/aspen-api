package com.zax.aspen.common.web.route

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 进程内固定窗口限流器
 *
 * 每个 key 维护一个「窗口起点 + 计数器」桶: 距起点不足窗口长度时计数递增并按上限放行,
 * 超过窗口后整体替换为新桶 (compute 原子语义, 窗口自首次命中起算而非对齐墙钟);
 * 计数是纯内存原子操作, 纳秒级、无外部 IO, 语义是「每实例配额」, 多实例部署时
 * 全局限量需由公共层替换为 Redis 实现。过期桶在容量超过阈值时惰性清扫, 不起后台线程;
 * 窗口切换瞬间旧桶上的并发计数可能与新桶短暂并存, 属固定窗口算法的已知边界毛刺
 */
class FixedWindowRateLimiter(
    /** 时间源, 测试注入可控 Clock 验证窗口翻转, 生产取系统 UTC 时钟 */
    private val clock: Clock = Clock.systemUTC(),
) {
    /** key 到窗口桶的映射, 容量超过该阈值时触发一次过期清扫 */
    private val windows = ConcurrentHashMap<String, Window>()

    /**
     * 尝试为 key 获取一次配额
     *
     * @param key 限流主体键 (映射 pattern + HTTP 方法 + scope 主体)
     * @param limit 窗口内允许的最大次数, 非正值视为不限流
     * @param windowSeconds 窗口长度 (秒), 非正值视为不限流
     * @return 本次请求在配额内返回 `true`; 超限返回 `false`
     */
    fun tryAcquire(key: String, limit: Int, windowSeconds: Int): Boolean {
        if (limit <= 0 || windowSeconds <= 0) {
            return true
        }
        val now = clock.instant().epochSecond
        sweepExpiredIfCrowded(now)
        // compute 的 Kotlin 映射类型可空, 本映射函数恒返回非 null, 空值仅作防御性放行
        val window = windows.compute(key) { _, existing ->
            if (existing == null || now - existing.startEpochSecond >= existing.windowSeconds) {
                Window(now, windowSeconds)
            } else {
                existing
            }
        } ?: return true
        return window.count.incrementAndGet() <= limit
    }

    /**
     * 容量超阈值时清扫全部过期窗口桶
     *
     * @param now 当前 epoch 秒
     */
    private fun sweepExpiredIfCrowded(now: Long) {
        if (windows.size <= SWEEP_THRESHOLD) {
            return
        }
        windows.entries.removeIf { entry -> now - entry.value.startEpochSecond >= entry.value.windowSeconds }
    }

    /** 单个 key 的窗口桶: 起点与窗口长度不可变, 计数并发递增 */
    private class Window(
        val startEpochSecond: Long,
        val windowSeconds: Int,
    ) {
        val count = AtomicLong()
    }

    private companion object {
        /** 缓存桶容量上限, key 数以「端点数 × 主体数」为上界, 超限即触发清扫 */
        private const val SWEEP_THRESHOLD = 65_536
    }
}

package com.zax.aspen.task.biz.service.task

import com.zax.aspen.task.api.enums.TaskTriggerSource
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 验证逻辑执行标识的构造规则
 *
 * 同轮幂等 (同任务同时点同租户同标识)、跨轮唯一 (不同时点不同标识)、
 * 人工触发按 requestId 幂等
 */
class TaskExecutionIdsTest {
    /** 验证同任务同时点同租户生成相同标识, 不同租户生成不同标识 */
    @Test
    fun `scheduled ids are stable per round and tenant`() {
        val fireTime = LocalDateTime.of(2026, 9, 12, 2, 0, 0)

        val first = TaskExecutionIds.scheduled(12L, fireTime, 100L)
        val second = TaskExecutionIds.scheduled(12L, fireTime, 100L)
        val otherTenant = TaskExecutionIds.scheduled(12L, fireTime, 200L)
        val otherRound = TaskExecutionIds.scheduled(12L, fireTime.plusMinutes(1), 100L)

        assertEquals(first, second)
        assertEquals("task-12", first.substringBefore("-f"))
        assertTrue(first.endsWith("-100"), "标识应以租户 id 收尾")
        assertNotEquals(first, otherTenant)
        assertNotEquals(first, otherRound)
    }

    /** 验证人工触发按 requestId 幂等且与计划触发互不冲突 */
    @Test
    fun `manual ids are request scoped`() {
        val fireTime = LocalDateTime.of(2026, 9, 12, 2, 0, 0)

        val first = TaskExecutionIds.manual(12L, "req-1", 100L)
        val second = TaskExecutionIds.manual(12L, "req-1", 100L)
        val otherRequest = TaskExecutionIds.manual(12L, "req-2", 100L)
        val scheduled = TaskExecutionIds.scheduled(12L, fireTime, 100L)

        assertEquals(first, second)
        assertNotEquals(first, otherRequest)
        assertNotEquals(first, scheduled)
    }

    /** 验证按来源构造的入口与手工构造一致 */
    @Test
    fun `build delegates by trigger source`() {
        val fireTime = LocalDateTime.of(2026, 9, 12, 2, 0, 0)

        assertEquals(
            TaskExecutionIds.scheduled(3L, fireTime, 5L),
            TaskExecutionIds.build(3L, TaskTriggerSource.SCHEDULED, fireTime, null, 5L),
        )
        assertEquals(
            TaskExecutionIds.manual(3L, "req-9", 5L),
            TaskExecutionIds.build(3L, TaskTriggerSource.MANUAL, fireTime, "req-9", 5L),
        )
    }
}

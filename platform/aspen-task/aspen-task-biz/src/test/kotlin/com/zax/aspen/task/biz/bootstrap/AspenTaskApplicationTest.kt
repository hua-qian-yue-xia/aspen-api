package com.zax.aspen.task.biz.bootstrap

import com.zax.aspen.common.cache.support.AspenCacheOperations
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.quartz.Scheduler
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * 验证 Task Web 装配可在不连接外部基础设施时完成
 *
 * Quartz 自动装配整体排除 (application.yaml 的集群属性对 RAMJobStore 不适用),
 * 以 Mock Scheduler 满足装配; 启动对账器的各步骤带安全包装, 在替身下记录错误不阻断
 */
@SpringBootTest(
    classes = [AspenTaskApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.babyfish.jimmer.spring.cfg.JimmerAutoConfiguration," +
            "org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration",
        // 测试上下文不连接外部 Nacos, 生产环境仍必须显式声明 Nacos Config Import
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
    ],
)
@Import(AspenTaskApplicationTest.TestTools::class)
class AspenTaskApplicationTest {
    /**
     * 验证无数据库上下文的自动装配后仍可加载 Task 应用上下文
     */
    @Test
    fun contextLoadsWithoutDatabase() {
    }

    /**
     * 无数据库上下文的装配占位
     */
    @TestConfiguration
    class TestTools {
        /**
         * 常驻的任务仓储需要 KSqlClient, 用 Mock 满足装配, 不触发真实查询
         *
         * @return 不执行真实查询的 KSqlClient Mock 实例
         */
        @Bean("aspenTestSqlClient")
        fun aspenTestSqlClient(): KSqlClient = Mockito.mock(KSqlClient::class.java)

        /**
         * 租户来源客户端需要缓存操作类, 用 Mock 满足装配 (测试不连接 Redis)
         *
         * @return 不执行真实读写 的 AspenCacheOperations Mock 实例
         */
        @Bean
        fun aspenTestCacheOperations(): AspenCacheOperations = Mockito.mock(AspenCacheOperations::class.java)

        /**
         * 同步器、对账器与桥接组件需要 Quartz Scheduler, 用 Mock 满足装配 (测试不启调度);
         * SchedulerContextBridge 在生命周期阶段写入 scheduler.context, Mock 默认返回
         * null 会炸桥接, 打桩返回真实可写的 SchedulerContext
         *
         * @return context 可写、不执行真实调度的 Scheduler Mock 实例
         */
        @Bean
        fun aspenTestScheduler(): Scheduler = Mockito.mock(Scheduler::class.java).also { scheduler ->
            Mockito.`when`(scheduler.context).thenReturn(org.quartz.SchedulerContext())
        }
    }
}

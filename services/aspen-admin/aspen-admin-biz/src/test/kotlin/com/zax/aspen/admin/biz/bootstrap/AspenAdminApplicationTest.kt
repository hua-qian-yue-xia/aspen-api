package com.zax.aspen.admin.biz.bootstrap

import org.babyfish.jimmer.sql.kt.KSqlClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * 验证 Admin Web 装配可在不连接外部基础设施时完成
 */
@SpringBootTest(
    classes = [AspenAdminApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.babyfish.jimmer.spring.cfg.JimmerAutoConfiguration",
        // 单元上下文测试不连接外部 Nacos, 生产环境仍必须显式声明 Nacos Config Import
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
    ],
)
@Import(AspenAdminApplicationTest.TestTools::class)
class AspenAdminApplicationTest {
    /**
     * 验证测试隔离数据库自动装配后仍可加载 Admin 应用上下文
     */
    @Test
    fun contextLoadsWithoutDatabaseOrRedis() {
    }

    /**
     * 无数据库上下文的装配占位
     */
    @TestConfiguration
    class TestTools {
        /**
         * 常驻的 sys 字典仓储需要 KSqlClient, 用 Mock 满足装配, 不触发真实查询
         *
         * @return 不执行真实查询的 KSqlClient Mock 实例
         */
        @Bean("aspenTestSqlClient")
        fun aspenTestSqlClient(): KSqlClient = Mockito.mock(KSqlClient::class.java)
    }
}

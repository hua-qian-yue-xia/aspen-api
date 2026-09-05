package com.zax.aspen

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** 验证迁移期间根模块可以在隔离外部基础设施后加载 */
@SpringBootTest(
    properties = [
        // 单元上下文测试不连接外部 Nacos, 生产环境仍必须显式声明 Nacos Config Import
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        // 这里只验证 Spring 基础上下文, Jimmer/MySQL 需要在后续真实数据库集成测试中验证
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    ],
)
class AspenApplicationTests {

    /** 验证 Spring 基础上下文可以成功启动 */
    @Test
    fun contextLoads() {
    }

}

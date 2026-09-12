package com.zax.aspen.storage.biz.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * 验证 Storage Web 装配可在不连接外部基础设施时完成
 */
@SpringBootTest(
    classes = [AspenStorageApplication::class],
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
class AspenStorageApplicationTest {
    /**
     * 验证无数据库上下文的自动装配后仍可加载 Storage 应用上下文
     */
    @Test
    fun contextLoadsWithoutDatabase() {
    }
}

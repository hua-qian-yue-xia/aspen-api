package com.zax.aspen.admin.biz.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** 验证 Admin Web 装配可在不连接外部基础设施时完成 */
@SpringBootTest(
    classes = [AspenAdminApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.babyfish.jimmer.spring.cfg.JimmerAutoConfiguration",
    ],
)
class AspenAdminApplicationTest {
    /** 验证测试隔离数据库自动装配后仍可加载 Admin 应用上下文 */
    @Test
    fun contextLoadsWithoutDatabaseOrRedis() {
    }
}

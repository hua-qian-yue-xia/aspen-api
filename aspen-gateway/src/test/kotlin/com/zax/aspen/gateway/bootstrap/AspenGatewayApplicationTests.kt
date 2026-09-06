package com.zax.aspen.gateway.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** 验证网关应用在隔离外部基础设施后可以加载基础上下文 */
@SpringBootTest(
    properties = [
        // 单元上下文测试不连接外部 Nacos, 生产环境经 spring.config.import 加载分层配置
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
    ],
)
class AspenGatewayApplicationTests {

    /** 验证网关基础上下文(路由装配、安全链、缓存基础设施)可以成功启动 */
    @Test
    fun contextLoads() {
    }
}

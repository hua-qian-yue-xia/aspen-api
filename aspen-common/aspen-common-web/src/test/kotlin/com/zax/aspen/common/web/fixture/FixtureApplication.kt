package com.zax.aspen.common.web.fixture

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * 受众路径前缀测试的装配载体
 *
 * 只扫描 fixture 包内的样例 Controller; 模块自身的自动装配经 classpath 的
 * AutoConfiguration.imports 随 @EnableAutoConfiguration 生效, 测试不额外手工装配
 */
@SpringBootApplication
class FixtureApplication

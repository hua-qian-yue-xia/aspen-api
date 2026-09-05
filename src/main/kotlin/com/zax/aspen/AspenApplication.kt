package com.zax.aspen

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 提供迁移期间根模块的 Spring Boot 启动入口 */
@SpringBootApplication
class AspenApplication

/** 使用命令行参数启动根模块应用 */
fun main(args: Array<String>) {
    runApplication<AspenApplication>(*args)
}

package com.zax.aspen.admin.biz.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 定义 Admin 微服务唯一的 Spring Boot 启动配置
 */
@SpringBootApplication(scanBasePackages = ["com.zax.aspen.admin.biz"])
class AspenAdminApplication

/**
 * 使用命令行参数启动 Admin 微服务
 *
 * @param args 命令行参数, 原样传给 Spring Boot 运行时
 */
fun main(args: Array<String>) {
    runApplication<AspenAdminApplication>(*args)
}

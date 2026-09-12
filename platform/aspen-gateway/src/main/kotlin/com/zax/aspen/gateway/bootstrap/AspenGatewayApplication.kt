package com.zax.aspen.gateway.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 定义 Gateway 微服务唯一的 Spring Boot 启动配置, 基于 WebFlux 的边缘入口
 */
@SpringBootApplication
class AspenGatewayApplication

/**
 * 使用命令行参数启动 Gateway 微服务
 *
 * @param args 命令行参数, 原样传给 Spring Boot 运行时
 */
fun main(args: Array<String>) {
    runApplication<AspenGatewayApplication>(*args)
}

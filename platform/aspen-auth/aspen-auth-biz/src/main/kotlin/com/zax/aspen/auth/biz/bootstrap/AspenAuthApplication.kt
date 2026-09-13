package com.zax.aspen.auth.biz.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 统一认证服务的启动入口
 *
 * 登录协议编排、令牌签发与刷新、验证码闸门与登录审计的唯一运行时;
 * 主体数据归各端用户域, 本服务经 Principal SPI 访问 (技术架构 14.2)
 */
@SpringBootApplication
class AspenAuthApplication

/** main 入口, Spring Boot 约定启动 */
fun main(args: Array<String>) {
    runApplication<AspenAuthApplication>(*args)
}

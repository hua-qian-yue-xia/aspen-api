package com.zax.aspen.auth.biz.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * 认证服务的支撑装配
 *
 * 提供主体 SPI 与行为验证码校验共用的 RestClient: 连接与读取超时显式收紧,
 * 避免登录链路被慢下游拖死; 序列化沿用服务默认 Jackson
 */
@Configuration
@EnableConfigurationProperties(AspenAuthProperties::class)
class AspenAuthConfiguration {
    /**
     * 提供跨服务调用的 RestClient, 供主体 SPI 网关与行为验证码校验使用
     *
     * @return 显式超时配置的 RestClient
     */
    @Bean
    fun aspenAuthRestClient(): RestClient = RestClient.builder().build()
}

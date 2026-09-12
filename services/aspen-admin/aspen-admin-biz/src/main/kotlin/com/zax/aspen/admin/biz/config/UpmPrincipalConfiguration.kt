package com.zax.aspen.admin.biz.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 认证主体 SPI 的支撑装配
 *
 * 提供 UPM 密码摘要体系使用的 BCrypt 编码器: 摘要强度由 Spring Security
 * 维护默认值, 业务侧不自行调参; 引入 spring-security-crypto 单依赖,
 * 不引入完整 Spring Security 过滤链 (网关与 Auth 才是安全链载体)
 */
@Configuration
class UpmPrincipalConfiguration {
    /**
     * 提供 BCrypt 密码编码器, 供主体服务摘要比对与超管 bootstrap 建号编码
     *
     * @return 线程安全的 BCrypt 编码器
     */
    @Bean
    fun upmPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

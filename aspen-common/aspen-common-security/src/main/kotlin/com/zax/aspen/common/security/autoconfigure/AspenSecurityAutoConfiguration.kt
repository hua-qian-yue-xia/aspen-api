package com.zax.aspen.common.security.autoconfigure

import com.zax.aspen.common.database.autoconfigure.AspenDatabaseAutoConfiguration
import com.zax.aspen.common.database.tenant.TenantContextSupplier
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.trust.InternalTrustFilter
import com.zax.aspen.common.security.trust.RequestIdentityContext
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered

/**
 * 注册业务进程最小信任链
 *
 * 只依赖 Servlet 能力与 common-database 的租户契约, 无 Redis 依赖; 信任链
 * (InternalTrustFilter + TenantContextSupplier) 经 aspen.security.enabled 开关
 * 控制, before 固定在 AspenDatabaseAutoConfiguration 之前注册
 * TenantContextSupplier, 保证 common-database 的 @ConditionalOnBean 求值时
 * 供给器已就位; 曾注册的客户端配置快照分发设施已随端配置回归 Auth 本库直读
 * (2026-09-13 归属修订) 移除
 */
@AutoConfiguration(before = [AspenDatabaseAutoConfiguration::class])
@ConditionalOnClass(InternalTrustFilter::class)
@EnableConfigurationProperties(AspenSecurityProperties::class)
class AspenSecurityAutoConfiguration {
    /**
     * 注册最小信任链过滤器
     *
     * 注册序低于 Trace ID 过滤器 (最高优先级), 与既有装配序对齐; 信任凭据从
     * 配置读入后构造期固化, 运行期不再读取
     *
     * @param properties 安全模块配置
     * @return 信任链过滤器的注册件
     */
    @Bean("aspenInternalTrustFilter")
    @ConditionalOnProperty(prefix = "aspen.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun aspenInternalTrustFilter(properties: AspenSecurityProperties): FilterRegistrationBean<InternalTrustFilter> =
        FilterRegistrationBean(InternalTrustFilter(properties.trustToken)).apply { order = Ordered.HIGHEST_PRECEDENCE + 20 }

    /**
     * 装配请求级租户上下文供给器
     *
     * 读取经信任链校验的身份上下文, 缺失时返回 null, common-database 的
     * fail-closed 租户链随之生效
     *
     * @return 读取 RequestIdentityContext 的租户供给器
     */
    @Bean("aspenTrustTenantContextSupplier")
    @ConditionalOnProperty(prefix = "aspen.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun aspenTrustTenantContextSupplier(): TenantContextSupplier = TenantContextSupplier {
        RequestIdentityContext.get()?.tenantId
    }
}

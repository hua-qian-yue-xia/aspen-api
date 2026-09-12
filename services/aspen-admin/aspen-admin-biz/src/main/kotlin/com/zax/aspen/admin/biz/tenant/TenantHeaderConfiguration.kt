package com.zax.aspen.admin.biz.tenant

import com.zax.aspen.common.database.tenant.TenantContextSupplier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * 租户头装配: 按 opt-in 开关注册租户头过滤器与 TenantContextSupplier
 *
 * aspen.admin.tenant-header.enabled 开启后, Admin 以内网调用链传入的
 * X-Aspen-Tenant-Id 建立租户上下文 (统一任务服务的逐租户投递、未来网关透传),
 * common-database 的 fail-closed 过滤器随之对租户数据生效; 默认关闭, 待
 * Gateway/Auth 统一认证就绪后由安全链取代本装配
 */
@Configuration
@ConditionalOnProperty(prefix = "aspen.admin.tenant-header", name = ["enabled"], havingValue = "true")
class TenantHeaderConfiguration {
    /**
     * 注册租户头过滤器
     *
     * 注册序低于 Trace ID 过滤器 (最高优先级), 保证租户上下文覆盖完整业务周期
     *
     * @return 租户头过滤器的注册件
     */
    @Bean
    fun tenantHeaderFilter(): FilterRegistrationBean<TenantHeaderFilter> =
        FilterRegistrationBean(TenantHeaderFilter()).apply { order = Ordered.HIGHEST_PRECEDENCE + 10 }

    /**
     * 装配请求级租户上下文供给器
     *
     * @return 读取 TenantHeaderContext 的供给器, 缺失时返回 null (fail-closed)
     */
    @Bean
    fun tenantContextSupplier(): TenantContextSupplier = TenantContextSupplier { TenantHeaderContext.get() }
}

package com.zax.aspen.common.database.autoconfigure

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.database.audit.AuditDraftInterceptor
import com.zax.aspen.common.database.enums.AspenEnumProviders
import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.common.database.tenant.TenantContextSupplier
import com.zax.aspen.common.database.tenant.TenantDraftInterceptor
import com.zax.aspen.common.database.tenant.TenantFilter
import org.babyfish.jimmer.spring.cfg.JimmerAutoConfiguration
import org.babyfish.jimmer.sql.DraftInterceptor
import org.babyfish.jimmer.sql.runtime.ScalarProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.type.filter.AssignableTypeFilter
import java.time.Clock

/** 注册 Jimmer 公共审计能力、租户隔离设施、枚举映射和数据库操作限制 */
@AutoConfiguration(before = [JimmerAutoConfiguration::class])
@ConditionalOnClass(DraftInterceptor::class)
@EnableConfigurationProperties(AspenDatabaseProperties::class)
class AspenDatabaseAutoConfiguration {
    /** 提供默认 UTC 时钟, 业务服务可以声明 Clock Bean 覆盖 */
    @Bean("aspenDatabaseClock")
    @ConditionalOnMissingBean(Clock::class)
    fun aspenDatabaseClock(): Clock = Clock.systemUTC()

    /** 根据配置创建分页和批处理限制 */
    @Bean("aspenDatabaseLimits")
    @ConditionalOnMissingBean(DatabaseLimits::class)
    fun aspenDatabaseLimits(properties: AspenDatabaseProperties): DatabaseLimits = DatabaseLimits(
        defaultPageSize = properties.pagination.defaultSize,
        maxPageSize = properties.pagination.maxSize,
        defaultBatchSize = properties.batch.defaultSize,
        maxBatchSize = properties.batch.maxSize,
    )

    /** 在审计功能开启时注册 Jimmer Draft 拦截器 */
    @Bean("aspenAuditDraftInterceptor")
    @ConditionalOnMissingBean(name = ["aspenAuditDraftInterceptor"])
    @ConditionalOnProperty(
        prefix = "aspen.database.audit",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun aspenAuditDraftInterceptor(clock: Clock): AuditDraftInterceptor = AuditDraftInterceptor(clock)

    /** 在服务装配租户上下文时为全部租户实体查询自动追加租户条件 */
    @Bean("aspenTenantFilter")
    @ConditionalOnBean(TenantContextSupplier::class)
    @ConditionalOnMissingBean(TenantFilter::class)
    fun aspenTenantFilter(tenantContextSupplier: TenantContextSupplier): TenantFilter = TenantFilter(tenantContextSupplier)

    /** 在服务装配租户上下文时自动填充新增数据的租户标识 */
    @Bean("aspenTenantDraftInterceptor")
    @ConditionalOnBean(TenantContextSupplier::class)
    @ConditionalOnMissingBean(TenantDraftInterceptor::class)
    fun aspenTenantDraftInterceptor(
        tenantContextSupplier: TenantContextSupplier,
    ): TenantDraftInterceptor = TenantDraftInterceptor(tenantContextSupplier)

    /** 扫描 AspenEnum 枚举并注册按 code 互转的 Jimmer 标量转换器 */
    @Bean("aspenEnumScalarProviders")
    @ConditionalOnMissingBean(name = ["aspenEnumScalarProviders"])
    fun aspenEnumScalarProviders(properties: AspenDatabaseProperties): List<ScalarProvider<*, *>> {
        val scanner = org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AssignableTypeFilter(AspenEnum::class.java))
        return properties.enums.basePackages.flatMap { basePackage ->
            scanner.findCandidateComponents(basePackage)
                .mapNotNull { candidate -> candidate.beanClassName }
                .mapNotNull { className -> runCatching { Class.forName(className) }.getOrNull() }
                .filter { enumType -> enumType.isEnum && AspenEnum::class.java.isAssignableFrom(enumType) }
                .map { enumType -> AspenEnumProviders.create(enumType) }
        }
    }
}

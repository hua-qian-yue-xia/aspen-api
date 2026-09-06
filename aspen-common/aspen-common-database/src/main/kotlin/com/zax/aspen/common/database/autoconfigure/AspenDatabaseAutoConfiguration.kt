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
import org.babyfish.jimmer.sql.kt.cfg.KCustomizer
import org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl
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
    /**
     * 提供默认 UTC 时钟供公共审计拦截器写入时间, 业务服务可声明自己的 Clock Bean 覆盖
     *
     * @return 基于 UTC 的系统时钟
     */
    @Bean("aspenDatabaseClock")
    @ConditionalOnMissingBean(Clock::class)
    fun aspenDatabaseClock(): Clock = Clock.systemUTC()

    /**
     * 根据 aspen.database 配置创建分页和批处理限制
     *
     * @param properties Aspen 数据库公共配置, 提供分页与批处理的默认值和上限
     * @return 供 Repository 与 Service 校验复用的数据库操作限制
     */
    @Bean("aspenDatabaseLimits")
    @ConditionalOnMissingBean(DatabaseLimits::class)
    fun aspenDatabaseLimits(properties: AspenDatabaseProperties): DatabaseLimits = DatabaseLimits(
        defaultPageSize = properties.pagination.defaultSize,
        maxPageSize = properties.pagination.maxSize,
        defaultBatchSize = properties.batch.defaultSize,
        maxBatchSize = properties.batch.maxSize,
    )

    /**
     * 在 aspen.database.audit.enabled 开启时注册 Jimmer Draft 拦截器, 自动写入审计实体的创建和更新时间
     *
     * @param clock 审计时间来源, 使用部署域统一时区
     * @return 公共审计时间拦截器
     */
    @Bean("aspenAuditDraftInterceptor")
    @ConditionalOnMissingBean(name = ["aspenAuditDraftInterceptor"])
    @ConditionalOnProperty(
        prefix = "aspen.database.audit",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun aspenAuditDraftInterceptor(clock: Clock): AuditDraftInterceptor = AuditDraftInterceptor(clock)

    /**
     * 在服务装配租户上下文时为全部租户实体查询自动追加租户条件
     *
     * @param tenantContextSupplier 当前请求租户标识来源
     * @return 租户隔离查询过滤器
     */
    @Bean("aspenTenantFilter")
    @ConditionalOnBean(TenantContextSupplier::class)
    @ConditionalOnMissingBean(TenantFilter::class)
    fun aspenTenantFilter(tenantContextSupplier: TenantContextSupplier): TenantFilter = TenantFilter(tenantContextSupplier)

    /**
     * 在服务装配租户上下文时自动填充新增数据的租户标识
     *
     * @param tenantContextSupplier 当前请求租户标识来源
     * @return 租户标识填充拦截器
     */
    @Bean("aspenTenantDraftInterceptor")
    @ConditionalOnBean(TenantContextSupplier::class)
    @ConditionalOnMissingBean(TenantDraftInterceptor::class)
    fun aspenTenantDraftInterceptor(
        tenantContextSupplier: TenantContextSupplier,
    ): TenantDraftInterceptor = TenantDraftInterceptor(tenantContextSupplier)

    /**
     * 扫描配置包内实现 AspenEnum 的枚举并经 KCustomizer 把按 code 互转的标量转换器注册进 Jimmer 客户端
     *
     * @param properties Aspen 数据库公共配置, 其 enums.basePackages 指定枚举扫描范围
     * @return 挂载全部扫描结果的 KCustomizer, 由 Jimmer 在构建 KSqlClient 时统一应用
     */
    @Bean("aspenEnumScalarProviders")
    @ConditionalOnMissingBean(name = ["aspenEnumScalarProviders"])
    fun aspenEnumScalarProviders(properties: AspenDatabaseProperties): KCustomizer {
        val scanner = org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AssignableTypeFilter(AspenEnum::class.java))
        val providers = properties.enums.basePackages.flatMap { basePackage ->
            scanner.findCandidateComponents(basePackage)
                .mapNotNull { candidate -> candidate.beanClassName }
                .mapNotNull { className -> runCatching { Class.forName(className) }.getOrNull() }
                .filter { enumType -> enumType.isEnum && AspenEnum::class.java.isAssignableFrom(enumType) }
                .map { enumType -> AspenEnumProviders.create(enumType) }
        }
        // Jimmer 只收集容器内 KCustomizer/ScalarProvider 类型的 Bean, 聚合成 List 的 Bean 不可见,
        // 因此以单个 KCustomizer 统一挂载全部扫描结果
        return object : KCustomizer {
            override fun customize(dsl: KSqlClientDsl) {
                providers.forEach(dsl::addScalarProvider)
            }
        }
    }
}

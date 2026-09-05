package com.zax.aspen.common.database.autoconfigure

import com.zax.aspen.common.database.audit.AuditDraftInterceptor
import com.zax.aspen.common.database.policy.DatabaseLimits
import org.babyfish.jimmer.spring.cfg.JimmerAutoConfiguration
import org.babyfish.jimmer.sql.DraftInterceptor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.time.Clock

/** 注册 Jimmer 公共审计能力和数据库操作限制 */
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
}

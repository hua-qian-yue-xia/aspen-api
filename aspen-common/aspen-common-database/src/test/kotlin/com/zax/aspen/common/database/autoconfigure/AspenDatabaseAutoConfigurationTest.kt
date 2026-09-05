package com.zax.aspen.common.database.autoconfigure

import com.zax.aspen.common.database.audit.AuditDraftInterceptor
import com.zax.aspen.common.database.policy.DatabaseLimits
import com.zax.aspen.common.database.tenant.TenantContextSupplier
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证数据库公共模块的自动配置条件和启动校验 */
class AspenDatabaseAutoConfigurationTest {
    /** 创建只加载数据库公共自动配置的隔离上下文 */
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AspenDatabaseAutoConfiguration::class.java))

    /** 验证默认 Bean 不要求 DataSource 即可创建 */
    @Test
    fun `creates defaults without requiring a datasource`() {
        contextRunner.run { context ->
            assertTrue(context.containsBean("aspenDatabaseClock"))
            assertTrue(context.containsBean("aspenAuditDraftInterceptor"))
            assertEquals(200, context.getBean(DatabaseLimits::class.java).maxPageSize)
        }
    }

    /** 验证用户时钟可以覆盖默认 Bean 且审计可以显式关闭 */
    @Test
    fun `backs off for user clock and disabled audit`() {
        contextRunner
            .withUserConfiguration(CustomClockConfiguration::class.java)
            .withPropertyValues("aspen.database.audit.enabled=false")
            .run { context ->
                assertFalse(context.containsBean("aspenDatabaseClock"))
                assertFalse(context.containsBean("aspenAuditDraftInterceptor"))
                assertSame(CustomClockConfiguration.CLOCK, context.getBean(Clock::class.java))
            }
    }

    /** 验证非法分页限制会在应用启动阶段失败 */
    @Test
    fun `fails fast for invalid limits`() {
        contextRunner
            .withPropertyValues(
                "aspen.database.pagination.default-size=201",
                "aspen.database.pagination.max-size=200",
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
            }
    }

    /** 验证用户定义的限制和审计拦截器可以替换默认 Bean */
    @Test
    fun `backs off for user database beans`() {
        contextRunner
            .withUserConfiguration(CustomDatabaseBeansConfiguration::class.java)
            .run { context ->
                assertEquals(
                    setOf("customDatabaseLimits"),
                    context.getBeansOfType(DatabaseLimits::class.java).keys,
                )
                assertEquals(50, context.getBean(DatabaseLimits::class.java).maxPageSize)
                assertEquals(
                    setOf("aspenAuditDraftInterceptor"),
                    context.getBeansOfType(AuditDraftInterceptor::class.java).keys,
                )
            }
    }

    /** 验证租户设施只在服务装配租户上下文提供者时注册 */
    @Test
    fun `registers tenant beans only when supplier exists`() {
        contextRunner.run { context ->
            assertFalse(context.containsBean("aspenTenantFilter"))
            assertFalse(context.containsBean("aspenTenantDraftInterceptor"))
        }
        contextRunner
            .withUserConfiguration(TenantSupplierConfiguration::class.java)
            .run { context ->
                assertTrue(context.containsBean("aspenTenantFilter"))
                assertTrue(context.containsBean("aspenTenantDraftInterceptor"))
            }
    }

    /** 验证类路径扫描为 AspenEnum 枚举注册标量转换器 */
    @Test
    fun `registers scalar providers for scanned aspen enums`() {
        contextRunner.run { context ->
            val providers = context.getBean("aspenEnumScalarProviders") as List<*>

            assertTrue(providers.size >= 2, "应扫描到 Gender 与 EnabledStatus 两个公共枚举")
        }
    }

    /** 提供覆盖自动配置默认时钟的用户配置 */
    @Configuration(proxyBeanMethods = false)
    class CustomClockConfiguration {
        /** 注册测试使用的固定时钟 */
        @Bean
        fun clock(): Clock = CLOCK

        /** 保存用户配置共享的固定时钟 */
        companion object {
            /** 表示用户显式声明的 UTC 固定时钟 */
            val CLOCK: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        }
    }

    /** 提供租户上下文提供者的用户配置 */
    @Configuration(proxyBeanMethods = false)
    class TenantSupplierConfiguration {
        /** 注册返回固定租户的上下文提供者 */
        @Bean
        fun tenantContextSupplier(): TenantContextSupplier = TenantContextSupplier { 1L }
    }

    /** 提供覆盖默认数据库限制和审计拦截器的用户配置 */
    @Configuration(proxyBeanMethods = false)
    class CustomDatabaseBeansConfiguration {
        /** 注册业务服务自行定义的数据库操作限制 */
        @Bean
        fun customDatabaseLimits(): DatabaseLimits = DatabaseLimits(10, 50, 20, 100)

        /** 使用约定名称替换公共审计拦截器 */
        @Bean("aspenAuditDraftInterceptor")
        fun customAuditDraftInterceptor(clock: Clock): AuditDraftInterceptor = AuditDraftInterceptor(clock)
    }
}

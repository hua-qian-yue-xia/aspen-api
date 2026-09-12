package com.zax.aspen.common.web.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test

/**
 * 验证受众前缀配置的启动期校验
 *
 * 前缀是网关路由断言与 RBAC 受众标识的锚点, 非法配置必须在启动期失败
 * 而非静默拼出错误映射; 以 ApplicationContextRunner 断言上下文启动失败
 * 且失败消息指向具体配置键
 */
class AspenWebPropertiesValidationTest {
    /** 验证缺前导斜杠的前缀导致启动失败且消息指向配置键 */
    @Test
    fun `prefix without leading slash fails startup`() {
        ApplicationContextRunner()
            .withPropertyValues("aspen.web.admin-api.prefix=admin-api")
            .withConfiguration(AutoConfigurations.of(AspenWebAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().hasMessageContaining("aspen.web.admin-api.prefix")
            }
    }

    /** 验证尾随斜杠的前缀导致启动失败 */
    @Test
    fun `prefix with trailing slash fails startup`() {
        ApplicationContextRunner()
            .withPropertyValues("aspen.web.app-api.prefix=/app-api/")
            .withConfiguration(AutoConfigurations.of(AspenWebAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().hasMessageContaining("aspen.web.app-api.prefix")
            }
    }

    /** 验证两组受众配置相同前缀导致启动失败 */
    @Test
    fun `duplicate prefixes across audiences fail startup`() {
        ApplicationContextRunner()
            .withPropertyValues("aspen.web.device-api.prefix=/app-api")
            .withConfiguration(AutoConfigurations.of(AspenWebAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context).getFailure().hasMessageContaining("互不相同")
            }
    }
}

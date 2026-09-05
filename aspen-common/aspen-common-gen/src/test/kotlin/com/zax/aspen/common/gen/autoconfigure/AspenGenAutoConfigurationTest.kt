package com.zax.aspen.common.gen.autoconfigure

import com.zax.aspen.common.gen.boot.GenDictSink
import com.zax.aspen.common.gen.boot.GenDictStartupRunner
import com.zax.aspen.common.gen.scan.GenDictCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import kotlin.test.assertEquals

/** 验证枚举字典自动装配的条件开关与目录投递编排 */
class AspenGenAutoConfigurationTest {
    /** 验证开启播种且存在 Sink 时注册目录与 Runner, 目录被投递给全部 Sink */
    @Test
    fun `delivers scanned catalog to sinks when enabled`() {
        val firstReceived = mutableListOf<GenDictCatalog>()
        val secondReceived = mutableListOf<GenDictCatalog>()

        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AspenGenAutoConfiguration::class.java))
            .withPropertyValues(
                "aspen.gen.dict.enabled=true",
                "aspen.gen.dict.base-packages=com.zax.aspen.common.core",
            )
            // Boot 4 的 withBean 存在 vararg 重载, 尾随 lambda 会被当作 vararg 元素, 必须显式 Supplier
            .withBean(
                "firstSink",
                GenDictSink::class.java,
                Supplier { GenDictSink { catalog -> firstReceived += catalog } },
            )
            .withBean(
                "secondSink",
                GenDictSink::class.java,
                Supplier { GenDictSink { catalog -> secondReceived += catalog } },
            )
            .run { context ->
                assertThat(context).hasSingleBean(GenDictCatalog::class.java)
                assertThat(context).hasSingleBean(GenDictStartupRunner::class.java)

                // ApplicationContextRunner 不执行 Runner, 手动触发验证投递行为
                context.getBean(GenDictStartupRunner::class.java)
                    .run(DefaultApplicationArguments())

                assertEquals(1, firstReceived.size)
                assertEquals(1, secondReceived.size)
                assertEquals(
                    listOf("enabled_status", "gender", "risk_level"),
                    firstReceived.single().descriptors.map { it.dictCode },
                )
            }
    }

    /** 验证默认关闭时不注册目录与 Runner */
    @Test
    fun `registers nothing when disabled`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AspenGenAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).doesNotHaveBean(GenDictCatalog::class.java)
                assertThat(context).doesNotHaveBean(GenDictStartupRunner::class.java)
            }
    }

    /** 验证缺少 Sink 时只产出目录, 不注册 Runner */
    @Test
    fun `skips runner when no sink is present`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AspenGenAutoConfiguration::class.java))
            .withPropertyValues(
                "aspen.gen.dict.enabled=true",
                "aspen.gen.dict.base-packages=com.zax.aspen.common.core",
            )
            .run { context ->
                assertThat(context).hasSingleBean(GenDictCatalog::class.java)
                assertThat(context).doesNotHaveBean(GenDictStartupRunner::class.java)
            }
    }
}

package com.zax.aspen.common.gen.autoconfigure

import com.zax.aspen.common.gen.boot.GenDictSink
import com.zax.aspen.common.gen.boot.GenDictStartupRunner
import com.zax.aspen.common.gen.scan.GenDictCatalog
import com.zax.aspen.common.gen.scan.GenDictScanner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** 注册枚举字典扫描与启动投递编排, 目录去向由使用方提供的 GenDictSink 决定 */
@AutoConfiguration
@EnableConfigurationProperties(AspenGenProperties::class)
class AspenGenAutoConfiguration {
    /**
     * 在播种开启时扫描 @GenDict 枚举产出字典目录
     *
     * @param properties 绑定 aspen.gen 前缀的外部配置, 提供扫描包范围
     * @return 按字典编码升序排列且无重复编码的字典目录
     */
    @Bean("aspenGenDictCatalog")
    @ConditionalOnMissingBean(name = ["aspenGenDictCatalog"])
    @ConditionalOnProperty(prefix = "aspen.gen.dict", name = ["enabled"], havingValue = "true")
    fun aspenGenDictCatalog(properties: AspenGenProperties): GenDictCatalog =
        GenDictScanner(properties.dict.basePackages).scan()

    /**
     * 在播种开启且存在投递实现时, 启动扫描一次并把目录交给全部 Sink
     *
     * @param catalog 本服务扫描出的字典目录
     * @param genDictSinks 容器内全部目录投递实现
     * @return 启动期投递编排 Runner
     */
    @Bean("aspenGenDictStartupRunner")
    @ConditionalOnMissingBean(name = ["aspenGenDictStartupRunner"])
    @ConditionalOnBean(GenDictSink::class)
    @ConditionalOnProperty(prefix = "aspen.gen.dict", name = ["enabled"], havingValue = "true")
    fun aspenGenDictStartupRunner(
        catalog: GenDictCatalog,
        genDictSinks: List<GenDictSink>,
    ): GenDictStartupRunner = GenDictStartupRunner(catalog, genDictSinks)
}

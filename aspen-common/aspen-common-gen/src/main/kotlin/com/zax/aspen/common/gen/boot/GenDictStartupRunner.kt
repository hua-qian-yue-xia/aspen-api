package com.zax.aspen.common.gen.boot

import com.zax.aspen.common.gen.scan.GenDictCatalog
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 启动时把本服务扫描出的枚举字典目录投递给容器内的全部 GenDictSink
 *
 * 编排属于通用逻辑, 由 common-gen 装配; 使用方只提供 Sink 实现,
 * 不重复编写启动钩子
 */
class GenDictStartupRunner(
    /** 本服务扫描出的字典目录 */
    private val catalog: GenDictCatalog,
    /** 容器内全部目录投递实现 */
    private val genDictSinks: List<GenDictSink>,
) : ApplicationRunner {
    /**
     * 逐个投递目录, 重复启动由各 Sink 保证幂等
     *
     * 相对 ApplicationRunner 契约的附加语义: 忽略启动参数, 只负责把同一份目录
     * 顺序交给全部 Sink, 不重试也不感知各 Sink 的投递结果
     *
     * @param args 启动应用参数, 本实现不使用
     */
    override fun run(args: ApplicationArguments) {
        genDictSinks.forEach { sink -> sink.deliver(catalog) }
    }
}

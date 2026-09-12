plugins {
    // 纯测试模块: 只承载全仓架构边界测试 (注入/KDoc/Redis 访问/CacheKey 工厂),
    // 无 main 源码集, 不产出运行构件, 不参与服务部署, 因此只需要 Kotlin JVM 插件
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen repository-wide architecture boundary tests"

java {
    toolchain {
        // 与全仓其余模块一致, 编译与运行统一 Java 21, 避免开发机 JAVA_HOME 差异
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // 边界测试全部是源码文本扫描, 不依赖任何被测模块的项目依赖, 保持对全仓的中立视角;
    // 仓库根经 tasks.withType<Test> 的系统属性注入, 测试内不做目录回溯
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        // 严格处理 JSR-305 可空性, 与全仓编译参数保持一致
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // 架构测试扫描全仓源码, 仓库根以 rootProject 绝对路径经系统属性注入,
    // 测试内不回溯目录也不依赖运行工作目录, 扫描范围天然限定在仓库内
    systemProperty("aspen.repositoryRoot", rootProject.projectDir.absolutePath)
}

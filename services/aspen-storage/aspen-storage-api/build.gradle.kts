plugins {
    // API 只发布普通契约 JAR, 不应用 Spring Boot 插件, 也不产生可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Storage external API contracts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // API 不应用 Boot 插件, 只借用其版本平台对齐 Spring, Jackson 和 Validation 版本
    api(platform(libs.spring.boot.dependencies))

    // 契约引用 core 的 AspenEnum 枚举契约与 EnumColor 调色板, 不引入任何运行时基础设施
    api(project(AspenProjects.COMMON_CORE))

    // Storage 是单业务边界服务, 本轮只发布域枚举与存储配置参数结构;
    // HTTP 契约与 VO 随上传 API 实现轮补齐, 不提前引入 Spring Web 运行时
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

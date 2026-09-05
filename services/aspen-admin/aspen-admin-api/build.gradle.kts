plugins {
    // API 只发布普通契约 JAR, 不应用 Spring Boot 插件, 也不产生可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Admin external API contracts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // API 不应用 Boot 插件, 只借用其版本平台对齐 Spring, Jackson 和 Validation 版本
    api(platform(libs.spring.boot.dependencies))

    // 契约引用 core 的纯数据模型 (如字典目录), 不引入任何运行时基础设施
    api(project(AspenProjects.COMMON_CORE))

    // 仅保留 DTO, VO, HTTP 契约和校验所需 API, 不引入任何运行时基础设施;
    // Feign Client 等到真实消费方服务随其 Cloud 设施一起发布, 避免把 openfeign 运行时传染给全部使用方
    api(libs.spring.web)
    api(libs.jakarta.validation.api)
    implementation(libs.jackson.module.kotlin)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

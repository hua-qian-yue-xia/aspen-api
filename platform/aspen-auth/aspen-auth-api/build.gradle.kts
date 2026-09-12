plugins {
    // API 只发布普通契约 JAR, 不应用 Spring Boot 插件, 也不产生可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Auth external API contracts"

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

    // 仅保留 DTO 与 HTTP 契约所需 API: Principal SPI 由 Admin 实现、Auth 消费,
    // 登录端点契约由 Auth 实现、前端与网关公开路径对齐
    api(libs.spring.web)
    api(libs.jakarta.validation.api)
    implementation(libs.jackson.module.kotlin)

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

plugins {
    // Core 是可被 api 使用的纯 Kotlin Library, 不应用 Spring 或 Spring Boot 插件
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen framework-neutral core contracts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Core 故意不引入 Spring, Jackson, Jimmer 或 Redis, 保证协议基础类型保持轻量
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

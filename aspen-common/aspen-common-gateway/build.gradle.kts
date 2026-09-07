plugins {
    // 网关路由分发 SDK 是普通 Library JAR, 不生成可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen gateway route distribution protocol and SDK"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))

    // Redis 访问一律经 common-cache 受控操作类, 本模块不直接暴露 Redis 客户端类型
    api(project(AspenProjects.COMMON_CACHE))

    // 自动装配与 JSON 序列化只作为内部实现, 不进入公共 API
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
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

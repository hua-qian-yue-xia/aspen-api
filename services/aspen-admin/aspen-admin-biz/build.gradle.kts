plugins {
    // Biz 是 Admin 唯一的 Spring Boot 运行与部署单元
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Admin business service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(AspenProjects.ADMIN_API))
    implementation(project(AspenProjects.COMMON_DATABASE))
    implementation(project(AspenProjects.COMMON_GEN))

    // 当前阶段只提供可编译, 可启动的最小 MVC 运行时, 不提前引入未使用的基础设施
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    // 首个真实写路径 (字典播种) 用 Testcontainers MySQL 验证迁移与幂等
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
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

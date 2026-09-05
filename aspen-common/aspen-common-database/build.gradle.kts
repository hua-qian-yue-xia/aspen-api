plugins {
    // Database 是普通 Library JAR, KSP 只负责本模块的公共 Jimmer 映射模型
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Jimmer database conventions and auto-configuration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":aspen-common-core"))

    // 业务 Repository 需要直接编译 KSqlClient, Entity 注解和 Kotlin DSL, 因此只公开 Jimmer SQL Kotlin API
    api(libs.jimmer.sql.kotlin)
    // Jimmer KSP 生成代码引用该注解包, 这里只补注解, 不引入 Jackson 2 databind
    api(libs.jackson.annotations)
    ksp(libs.jimmer.ksp)

    // Jimmer Starter 和自动配置注解属于运行实现, 数据库驱动必须由具体 biz 使用 runtimeOnly 选择
    implementation(libs.jimmer.spring.boot.starter)
    implementation(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    kspTest(libs.jimmer.ksp)
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

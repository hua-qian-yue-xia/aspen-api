plugins {
    // Gen 是普通 Library JAR, 提供枚举字典扫描与目录模型, 不含持久化与数据库驱动
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen generation tooling and enum dictionary scanning"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(AspenProjects.COMMON_CORE))

    // 自动装配与类路径扫描属于运行实现, 不扩大使用方的编译 API
    implementation(libs.spring.boot.autoconfigure)

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

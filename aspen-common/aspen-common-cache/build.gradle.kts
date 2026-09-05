plugins {
    // Cache 是普通 Library JAR, 不生成可执行 bootJar, 也不隐式开启 @EnableCaching
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Redis cache conventions and auto-configuration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":aspen-common-core"))

    // Redis 和 Jackson 只实现公共缓存接口, 不作为 common-cache 的公共 API 暴露
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.jackson.module.kotlin)

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
